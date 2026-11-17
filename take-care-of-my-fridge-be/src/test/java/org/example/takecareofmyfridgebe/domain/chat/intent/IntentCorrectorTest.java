package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 의도 판단 뒤 서버가 AI 결과를 고치는 보정 규칙(IntentCorrector) 테스트.
 * 지금은 facade를 통해 보정 규칙을 시험한다(진짜 IntentCorrector를 넣은 ChatIntentFacade.resolve). correct()를 직접 부르는 방식은 나중에 바꾼다.
 * 흐름(칩, AI 실패, 다시 추천 합치기, 알레르기, 이어지는 말)은 ChatIntentFacadeTest에 있다.
 */
class IntentCorrectorTest {

    private static final Long MEMBER_ID = ChatIntentFixture.MEMBER_ID;
    private static final Long SESSION_ID = ChatIntentFixture.SESSION_ID;
    private static final Long TOFU = ChatIntentFixture.TOFU;

    private ChatIntentFixture fixture;
    private RecipeService recipeService;
    private IngredientMatcher ingredientMatcher;
    private ChatIntentFacade facade;

    @BeforeEach
    void setUp() {
        fixture = new ChatIntentFixture();
        recipeService = fixture.recipeService;
        ingredientMatcher = fixture.ingredientMatcher;
        facade = fixture.facade;
    }

    @Nested
    @DisplayName("요리 이름 키워드")
    class Keywords {

        @Test
        @DisplayName("Jev가 특정 요리로 보면 OpenAI가 꺼낸 요리 이름과 요리 표현을 검색 조건에 넣고, OpenAI 키워드는 버린다")
        void putsDishIntoQuery() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("국"), null, null, null, null,
                    List.of("냉면", "막국수"), "냉면", "냉면 같은 면 요리"));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("차가운 냉면 같은 면 요리"))
                    .recipeQuery();

            assertThat(query.keywords()).containsExactly("냉면");
            assertThat(query.keywordText()).isEqualTo("냉면");
            assertThat(query.preferenceText()).isEqualTo("냉면 같은 면 요리");
        }


        @Test
        @DisplayName("메시지의 단어 \"된찌\"의 글자가 OpenAI 요리 이름 된장찌개에 순서대로 다 있으면 줄임말을 푼 이름으로 받는다")
        void keepsExpandedDish_whenWordLettersAreInOrder() {
            givenExtracted(dish("된장찌개"));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("된찌 먹고 싶어")).recipeQuery();

            assertThat(query.keywordText()).isEqualTo("된장찌개");
            assertThat(query.keywords()).containsExactly("된장찌개");
        }

        @Test
        @DisplayName("메시지의 단어 \"김피탕\"의 글자가 OpenAI 요리 이름 김치찌개에 순서대로 다 있지 않으면 요리 이름을 버린다")
        void dropsDish_whenWordLettersAreNotInOrder() {
            givenExtracted(dish("김치찌개"));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김피탕 먹고 싶어")).recipeQuery();

            assertThat(query.keywordText()).isNull();
            assertThat(query.keywords()).doesNotContain("김치찌개");
        }

        @Test
        @DisplayName("OpenAI 요리 이름이 메시지에 그대로 있으면 지금처럼 받는다")
        void keepsDish_whenDishIsInMessage() {
            givenExtracted(dish("김치찌개"));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치 찌개 먹고 싶어")).recipeQuery();

            assertThat(query.keywordText()).isEqualTo("김치찌개");
        }

        // 요리 이름만 꺼낸 추천 (Jev는 특정 요리)
        private static IntentResult dish(String dishText) {
            return new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null, null, dishText, null);
        }
    }

    @Nested
    @DisplayName("이어짐 분류 (후속 질문)")
    class CategoryWords {

        // 직전 추천: 김피탕 같은 메뉴 (분류 튀김, 키워드 김피탕·탕수육·깐풍기)
        private final ChatContext afterKimpitang = new ChatContext(List.of(), LastRecommendation.of(
                new RecipeQuery(MEMBER_ID, List.of(), null, List.of("튀김"), null, null, false,
                        List.of("김피탕", "탕수육", "깐풍기"), "김피탕"), List.of(366L, 129L, 148L)),
                List.of(new CardName(366L, "깐풍기"), new CardName(129L, "라조기"), new CardName(148L, "라조육")));
        // 직전 추천: 디저트
        private final ChatContext afterDessert = new ChatContext(List.of(), LastRecommendation.of(
                new RecipeQuery(MEMBER_ID, List.of(), null, List.of("디저트"), null, null, false), List.of(1080L, 1493L, 1612L)),
                List.of(new CardName(1080L, "단호박푸딩"), new CardName(1493L, "건강 마늘 꿀환"), new CardName(1612L, "자색고구마찹쌀푸딩")));

        @BeforeEach
        void setUpCategories() {
            when(recipeService.getClassifications()).thenReturn(new RecipeClassificationResponse(List.of("한식"),
                    List.of("국", "기타", "디저트", "면류", "샐러드", "양념", "음료", "찌개", "튀김")));
        }

        @Test
        @DisplayName("디저트 추천 뒤 \"다른 디저트 추천해줘\"는 이어짐(직전 카드 빼기)이고 분류 디저트다")
        void followsWithDessert_whenAnotherDessert() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, null, null, null, null, null, null).withFollows(true));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("다른 디저트 추천해줘"), afterDessert);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.follows()).isTrue();
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("디저트");
        }

        @Test
        @DisplayName("디저트 추천 뒤 \"다른 거 추천해줘\"는 이어짐이고 직전 분류 디저트를 이어 간다")
        void keepsPreviousDessert_whenAnotherOne() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, null, null, null, null, null, null).withFollows(true));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("다른 거 추천해줘"), afterDessert);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.follows()).isTrue();
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("디저트");
        }

    }

    @Test
    @DisplayName("새 추천에서 Jev가 \"매운 거 말고 단 걸로\"를 매운맛 피함·단맛 원함으로 답하면 피할 맛 매운맛, 원하는 맛 단맛으로 둔다")
    void setsAvoidTastes_whenRecommend() {
        givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SWEET"), null, null, null, null, null, null,
                null, null, null, null, null, List.of("SPICY")));

        RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고 단 걸로")).recipeQuery();

        assertThat(query.avoidTastes()).containsExactly(Taste.SPICY);
        assertThat(query.wantedTastes()).containsExactly(Taste.SWEET);
    }

    @Nested
    @DisplayName("새 추천 맛 정리: 앞 대화에서 따라온 맛만 뺀다")
    class CarriedTastes {

        private ChatContext after(List<Taste> wanted, List<Taste> avoid) {
            RecipeQuery previous = new RecipeQuery(MEMBER_ID, List.of(), wanted, List.of(), null, List.of(), false,
                    List.of(), null, null).withAvoidTastes(avoid);
            return new ChatContext(List.of(), LastRecommendation.of(previous, List.of(10L)), List.of(new CardName(10L, "김치찌개")));
        }

        @Test
        @DisplayName("직전 조건이 없으면 맛 단어 목록에 없는 말(\"맵고\")이어도 AI가 넣은 맛을 그대로 둔다")
        void keepsAiTastes_whenNoPreviousCondition() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), null, null, null, null, false, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("맵고 따뜻한 스프")).recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
        }

        @Test
        @DisplayName("직전 피할 맛 매운맛 뒤 \"김치찌개\"면 따라온 피할 맛을 뺀다")
        void dropsCarriedAvoid() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, 0, List.of(), List.of(), false,
                    List.of("김치찌개"), null, null, null, null, List.of(), List.of("SPICY")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치찌개"),
                    after(List.of(), List.of(Taste.SPICY))).recipeQuery();

            assertThat(query.avoidTastes()).isEmpty();
        }

        @Test
        @DisplayName("직전 원하는 맛 매운맛 뒤 \"디저트\"면 따라온 원하는 맛을 뺀다")
        void dropsCarriedWanted() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), List.of("디저트"), null, null, null, false, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트"),
                    after(List.of(Taste.SPICY), List.of())).recipeQuery();

            assertThat(query.wantedTastes()).isEmpty();
        }

        @Test
        @DisplayName("직전 원하는 맛 매운맛이어도 이번 말에 그 맛 단어가 있으면 남긴다")
        void keepsSpokenTaste_whenAlsoPrevious() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), null, null, null, null, false, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 볶음 추천해줘"),
                    after(List.of(Taste.SPICY), List.of())).recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
        }
    }

    @Nested
    @DisplayName("서비스 규칙")
    class ServiceRules {

        private IntentCorrector corrector;

        @BeforeEach
        void setUpCorrector() {
            corrector = fixture.newCorrector();
        }

        private CorrectionInput input(String message) {
            return new CorrectionInput(MEMBER_ID, SESSION_ID, message, List.of("국", "찌개"), ChatContext.empty());
        }

        @Test
        @DisplayName("추천에서 요리 이름이 메시지에 띄어쓰기와 상관없이 그대로 있으면 두고, 없으면 비운다")
        void keepsSpokenDishOnly() {
            IntentResult inMessage = new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false,
                    List.of("나가사키짬뽕"), "나가사키짬뽕", null);
            IntentResult notInMessage = new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false,
                    List.of("탕수육"), "탕수육", null);

            assertThat(corrector.correct(input("나가사키 짬뽕 먹고 싶어"), inMessage).result().dishText()).isEqualTo("나가사키짬뽕");
            assertThat(corrector.correct(input("김피탕 먹고 싶어"), notInMessage).result().dishText()).isNull();
        }

        @Test
        @DisplayName("피하는 맛만 말했으면 뺄 조건 TASTE를 지우고, 맛 조건을 지우라는 말이 있으면 그대로 둔다")
        void keepsOtherTastes_whenOnlyAvoiding() {
            IntentResult removesTaste = new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), null, null, null, false, null, null, null,
                    null, null, List.of("TASTE"), List.of("SPICY"), null, null).withFollows(true);

            assertThat(corrector.correct(input("매운 건 빼줘"), removesTaste).result().removed()).isEmpty();
            assertThat(corrector.correct(input("맛 상관없이 매운 건 빼줘"), removesTaste).result().removed()).containsExactly("TASTE");
        }

        @Test
        @DisplayName("일반 답·냉장고 보기는 규칙을 거치지 않고 그대로 둔다")
        void keepsResult_whenNotRecommend() {
            IntentResult general = new IntentResult(ChatIntent.GENERAL, null, null, null, null, null, false,
                    List.of("탕수육"), "탕수육", null);

            IntentCorrector.Corrected corrected = corrector.correct(input("김피탕이 뭐야?"), general);

            assertThat(corrected.result()).isEqualTo(general);
            assertThat(corrected.detailTarget()).isNull();
        }
    }

    private void givenExtracted(IntentResult result) {
        fixture.givenAi(result);
    }

    private static Ingredient ingredient(Long id, String code, String name) {
        return ChatIntentFixture.ingredient(id, code, name);
    }
}
