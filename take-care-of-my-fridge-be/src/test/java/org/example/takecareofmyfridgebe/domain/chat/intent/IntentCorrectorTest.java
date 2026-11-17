package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
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
import org.example.takecareofmyfridgebe.domain.chat.facade.EvalCorrectionSwitches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("일반 대화로 본 말을 서버가 바꾸기")
    class GeneralToRecommend {
        @Test
        @DisplayName("레시피 말은 띄어 써도 지운다 (닭볶음탕 만드는 법 → 닭볶음탕, 잡채 만 들 기 → 잡채)")
        void removesSpacedRecipeWords() {
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("닭볶음탕 만드는 법")).isEqualTo("닭볶음탕");
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("잡채 만 들 기")).isEqualTo("잡채");
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("닭볶음탕레시피")).isEqualTo("닭볶음탕");
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("레시피")).isEmpty();
        }

        @Test
        @DisplayName("묻는 말(영양을 묻는 말 포함), 음식 말이 없는 말(안녕), 긴 말은 이름만 말한 말로 보지 않고 일반 답 그대로 둔다")
        void keepsGeneral_whenNotNameOnly() {
            givenExtracted(IntentResult.general());

            for (String message : List.of("밀키트 추천 기준이 뭐야?", "안녕", "오늘 날씨가 너무 좋아서 기분이 정말 좋아", "브로콜리 영양소 알려줘")) {
                assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message)).intent())
                        .as(message).isEqualTo(ChatIntent.GENERAL);
            }
            verify(recipeService, never()).suggestNames("밀키트추천기준이뭐야", 1);
        }

        @Test
        @DisplayName("AI가 GENERAL이라고 한 묻는 말(추천 기준·어떻게·보관)은 밀키트·분류·맛 단어가 있어도 서버가 추천으로 바꾸지 않는다")
        void keepsGeneral_whenQuestionMessages() {
            givenExtracted(IntentResult.general());

            for (String message : List.of("밀키트 추천 기준이 뭐야?", "레시피 추천 기준이 뭐야?", "디저트는 어떻게 골라?",
                    "매운 거 기준이 뭐야?", "두부 얼마나 보관돼?", "김치찌개 어떻게 만들어?")) {
                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                        new ChatMessageCreateRequest(message, ChatMessageMode.AUTO, List.of(TOFU), false));

                assertThat(resolution.intent()).as(message).isEqualTo(ChatIntent.GENERAL);
                assertThat(resolution.recipeQuery()).as(message).isNull();
            }
        }

    }

    @Nested
    @DisplayName("요리 이름 키워드")
    class Keywords {

        @Test
        @DisplayName("AI가 뽑은 요리 이름 키워드와 요리 표현을 검색 조건에 넣는다")
        void putsKeywordsIntoQuery() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("국"), null, null, null, null,
                    List.of("냉면", "막국수"), "냉면", "냉면 같은 면 요리"));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("차가운 냉면 같은 면 요리"))
                    .recipeQuery();

            assertThat(query.keywords()).containsExactly("냉면", "막국수");
            assertThat(query.keywordText()).isEqualTo("냉면");
            assertThat(query.preferenceText()).isEqualTo("냉면 같은 면 요리");
        }

        @Test
        @DisplayName("분류 이름·묶음 말은 \"요리·메뉴\"가 붙어도 그 분류(묶음 말은 목록에 있는 분류들)이고, 다른 말이 붙은 요리 이름은 분류가 아니다")
        void judgesCategoryWord() {
            IntentCorrector corrector = newCorrector(ChatCorrectionProperties.ALL_ON);
            List<String> categories = List.of("국", "디저트", "찌개", "면류");

            for (String word : List.of("디저트", "디저트 메뉴", "디저트 종류")) {
                assertThat(corrector.categoriesOf(word, categories)).as(word).contains(List.of("디저트"));
            }
            for (String word : List.of("국물 요리", "국물")) {
                assertThat(corrector.categoriesOf(word, categories)).as(word).contains(List.of("국", "찌개"));
            }
            assertThat(corrector.categoriesOf("면", categories)).contains(List.of("면류"));
            for (String word : List.of("김치찌개", "국수", "매운 국", "디저트 케이크", "요리", "푸딩")) {
                assertThat(corrector.categoriesOf(word, categories)).as(word).isEmpty();
            }
        }

    }

    @Nested
    @DisplayName("분류 단어 확인 (후속 질문)")
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
        @DisplayName("디저트 추천 뒤 \"다른 디저트 추천해줘\"는 다시 추천(직전 카드 빼기)이고 분류 디저트다")
        void refinesWithDessert_whenAnotherDessert() {
            givenExtracted(new IntentResult(ChatIntent.REFINE, null, List.of(), null, null, null, null, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("다른 디저트 추천해줘"), afterDessert);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.REFINE);
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("디저트");
        }

        @Test
        @DisplayName("디저트 추천 뒤 \"다른 거 추천해줘\"는 다시 추천이고 직전 분류 디저트를 이어 간다")
        void keepsPreviousDessert_whenAnotherOne() {
            givenExtracted(new IntentResult(ChatIntent.REFINE, null, List.of(), null, null, null, null, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("다른 거 추천해줘"), afterDessert);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.REFINE);
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("디저트");
        }

        @Test
        @DisplayName("\"국수 먹고 싶어\"는 분류 국으로 채우지 않고 AI 결과를 그대로 쓴다")
        void keepsAiResult_whenNoodleNotSoup() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("면류"), null, null, null, null,
                    List.of("국수", "잔치국수"), "국수", null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국수 먹고 싶어"), afterDessert)
                    .recipeQuery();

            assertThat(query.moodCategories()).containsExactly("면류");
            assertThat(query.keywords()).containsExactly("국수", "잔치국수");
        }

    }

    @Test
    @DisplayName("새 추천에서 \"매운 거 말고\"면 피할 맛 매운맛으로 두고, 원하는 맛과 겹치면 피할 맛이 이긴다")
    void setsAvoidTastes_whenRecommend() {
        givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY", "SWEET"), null, null, null, null, null, null,
                null, null, null, null, null, List.of("spicy")));

        RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고 단 걸로")).recipeQuery();

        assertThat(query.avoidTastes()).containsExactly(Taste.SPICY);
        assertThat(query.wantedTastes()).containsExactly(Taste.SWEET);
    }

    @Nested
    @DisplayName("냉장고 보기 (FRIDGE)")
    class Fridge {
        @Test
        @DisplayName("잘못 걸리기 쉬운 말은 바꾸지 않는다: 요리 말은 추천, 묻는 말과 주문 상태 질문은 일반 답")
        void keepsIntent_whenMisleadingMessages() {
            Map<String, ChatIntent> cases = new LinkedHashMap<>();
            cases.put("냉장고에 있는 재료로 뭐 먹지", ChatIntent.RECOMMEND);
            cases.put("냉장고에서 꺼낸 고기 어떻게 해동해?", ChatIntent.GENERAL);
            cases.put("유통기한 지난 거 먹어도 돼?", ChatIntent.GENERAL);
            cases.put("주문한 거 언제 와?", ChatIntent.GENERAL);
            cases.forEach((message, aiIntent) -> {
                givenExtracted(intentOnly(aiIntent));

                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message));

                assertThat(resolution.intent()).as(message).isEqualTo(aiIntent);
            });
        }

        private static IntentResult fridge(List<String> names, String scope) {
            return new IntentResult(ChatIntent.FRIDGE, List.of(), List.of(), 0, names, List.of(), false, List.of(), "", "",
                    0, "", List.of(), List.of(), "", scope);
        }

        private static IntentResult intentOnly(ChatIntent intent) {
            return new IntentResult(intent, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null);
        }
    }

    @Nested
    @DisplayName("사기 (SHOP)")
    class Shop {
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
    @DisplayName("보정 끄기 (app.chat.corrections)")
    class Corrections {

        // 끌 보정만 false
        private ChatCorrectionProperties off(String... names) {
            return EvalCorrectionSwitches.properties(java.util.Set.of(names));
        }

        @Test
        @DisplayName("새 추천 맛 정리를 끄면 직전 조건에서 따라온 맛도 지우지 않는다")
        void keepsCarriedTastes_whenSpokenTastesOff() {
            RecipeQuery spicy = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), List.of(), null, List.of(), false,
                    List.of(), null, null);
            ChatContext afterSpicy = new ChatContext(List.of(), LastRecommendation.of(spicy, List.of(10L)),
                    List.of(new CardName(10L, "김치찌개")));
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), List.of("디저트"), null, null, null, false, null, null, null));
            ChatMessageCreateRequest request = ChatMessageCreateRequest.of("디저트 추천해줘");

            RecipeQuery on = facade.resolve(MEMBER_ID, SESSION_ID, request, afterSpicy).recipeQuery();
            RecipeQuery offQuery = newFacade(off("spoken-tastes-only")).resolve(MEMBER_ID, SESSION_ID, request, afterSpicy).recipeQuery();

            assertThat(on.wantedTastes()).as("켜 두면 따라온 맛을 뺀다").isEmpty();
            assertThat(offQuery.wantedTastes()).containsExactly(Taste.SPICY);
        }

    }

    private ChatIntentFacade newFacade(ChatCorrectionProperties corrections) {
        return fixture.newFacade(corrections);
    }

    private ChatIntentFacade newFacade(ChatCorrectionProperties corrections, IntentCorrector corrector) {
        return fixture.newFacade(corrections, corrector);
    }

    private IntentCorrector newCorrector(ChatCorrectionProperties corrections) {
        return fixture.newCorrector(corrections);
    }

    private void givenExtracted(IntentResult result) {
        fixture.givenExtracted(result);
    }

    private static Ingredient ingredient(Long id, String code, String name) {
        return ChatIntentFixture.ingredient(id, code, name);
    }
}
