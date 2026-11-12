package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
        @DisplayName("밀키트를 직접 말했는데 AI가 GENERAL이라고 하면 추천으로 바꾸고, 밀키트 말고면 일반 답 그대로 둔다")
        void recommends_whenGeneralButMealkitSpoken() {
            givenExtracted(IntentResult.general());

            ChatIntentResolution mealkit = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("밀키트 뭐 있어?", ChatMessageMode.AUTO, List.of(), false));
            ChatIntentResolution notMealkit = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("밀키트 말고 보관법 알려줘", ChatMessageMode.AUTO, List.of(), false));

            assertThat(mealkit.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(mealkit.recipeQuery()).isNotNull();
            assertThat(notMealkit.intent()).isEqualTo(ChatIntent.GENERAL);
        }

        @Test
        @DisplayName("밀키트와 같이 \"있어·있나\"라고 하면 AI가 GENERAL이어도 추천으로 바꾸고, \"추천 기준 있어?\"는 일반 그대로 둔다")
        void recommends_whenMealkitWithExistenceWord() {
            givenExtracted(IntentResult.general());

            for (String message : List.of("국물 밀키트 있어?", "밀키트 있나?")) {
                assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message)).intent())
                        .as(message).isEqualTo(ChatIntent.RECOMMEND);
            }
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("밀키트 추천 기준 있어?")).intent())
                    .isEqualTo(ChatIntent.GENERAL);
        }

        @Test
        @DisplayName("이름만 말한 짧은 말은 AI가 GENERAL이어도 추천으로 바꾼다: 국물 밀키트(국 계열), 디저트(분류), 두부(재료), 김치찌개(키워드)")
        void recommends_whenNameOnly() {
            when(recipeService.getClassifications()).thenReturn(
                    new RecipeClassificationResponse(List.of("한식"), List.of("국", "디저트", "기타", "찌개")));
            when(ingredientMatcher.matchAll(List.of("두부"))).thenReturn(Map.of("두부", ingredient(TOFU, "ING_TOFU", "두부")));
            when(recipeService.suggestNames("김치찌개", 1)).thenReturn(List.of("김치찌개"));
            givenExtracted(IntentResult.general());

            ChatIntentResolution mealkit = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국물 밀키트"));
            ChatIntentResolution dessert = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트"));
            ChatIntentResolution tofu = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부"));
            ChatIntentResolution stew = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치찌개"));

            assertThat(List.of(mealkit, dessert, tofu, stew)).extracting(ChatIntentResolution::intent)
                    .containsOnly(ChatIntent.RECOMMEND);
            assertThat(mealkit.recipeQuery().moodCategories()).containsExactly("국", "찌개");
            assertThat(dessert.recipeQuery().moodCategories()).containsExactly("디저트");
            assertThat(tofu.recipeQuery().selectedIngredientIds()).containsExactly(TOFU);
            assertThat(stew.recipeQuery().keywords()).containsExactly("김치찌개");
            assertThat(stew.recipeQuery().keywordText()).isEqualTo("김치찌개");
        }

        @Test
        @DisplayName("\"매운 닭볶음탕 레시피\"는 레시피 말과 맛 단어를 빼고 닭볶음탕을 찾아 닭볶음탕 매운맛 추천으로 바꾼다")
        void recommendsDish_whenNameOnlyWithTasteAndRecipeWords() {
            when(recipeService.getClassifications()).thenReturn(
                    new RecipeClassificationResponse(List.of("한식"), List.of("국", "찜", "찌개")));
            when(ingredientMatcher.matchAll(anyList())).thenReturn(Map.of());
            when(recipeService.suggestNames(any(), anyInt())).thenReturn(List.of());
            when(recipeService.suggestNames("닭볶음탕", 1)).thenReturn(List.of("토마토시금치 닭볶음탕"));
            givenExtracted(IntentResult.general());

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 닭볶음탕 레시피"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().keywords()).containsExactly("닭볶음탕");
            assertThat(resolution.recipeQuery().keywordText()).isEqualTo("닭볶음탕");
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
            verify(recipeService).suggestNames("매운 닭볶음탕", 1);
        }

        @Test
        @DisplayName("\"매운탕 레시피\"처럼 맛 단어로 시작하는 요리 이름은 맛 단어를 빼지 않고 그대로 찾는다")
        void keepsDishStartingWithTasteWord() {
            when(ingredientMatcher.matchAll(anyList())).thenReturn(Map.of());
            when(recipeService.suggestNames("매운탕", 1)).thenReturn(List.of("우럭 매운탕"));
            givenExtracted(IntentResult.general());

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운탕 레시피"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().keywords()).containsExactly("매운탕");
        }

        @Test
        @DisplayName("레시피 말은 띄어 써도 지운다 (닭볶음탕 만드는 법 → 닭볶음탕, 잡채 만 들 기 → 잡채)")
        void removesSpacedRecipeWords() {
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("닭볶음탕 만드는 법")).isEqualTo("닭볶음탕");
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("잡채 만 들 기")).isEqualTo("잡채");
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("닭볶음탕레시피")).isEqualTo("닭볶음탕");
            assertThat(newCorrector(ChatCorrectionProperties.ALL_ON).withoutRecipeWords("레시피")).isEmpty();
        }

        @Test
        @DisplayName("인사·지난 일을 묻는 짧은 말(밥 먹었어?, 안녕 밥은?)은 재료 이름이 있어도 일반 답 그대로 두고, \"밥 추천\"은 추천으로 바꾼다")
        void keepsGeneral_whenGreetingOrPastQuestion() {
            Ingredient rice = ingredient(70L, "ING_RICE", "밥");
            when(ingredientMatcher.matchAll(anyList())).thenAnswer(invocation -> {
                List<String> names = invocation.getArgument(0);
                return names.contains("밥") ? Map.of("밥", rice) : Map.of();
            });
            givenExtracted(IntentResult.general());

            for (String message : List.of("밥 먹었어?", "안녕 밥은?")) {
                assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message)).intent())
                        .as(message).isEqualTo(ChatIntent.GENERAL);
            }
            ChatIntentResolution recommend = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("밥 추천"));

            assertThat(recommend.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(recommend.recipeQuery().selectedIngredientIds()).containsExactly(70L);
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
        @DisplayName("\"국물\" 같은 묶음 말은 서버가 추천으로 바꾼 경우와 AI가 추천한 경우 모두 국 계열 분류로 채운다")
        void fillsGroupCategories_whenGroupWordSpoken() {
            givenExtracted(IntentResult.general());
            ChatIntentResolution converted = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국물 밀키트 있어?"));

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false, null, null, null));
            ChatIntentResolution recommended = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국물 요리 추천해줘"));

            assertThat(converted.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(converted.recipeQuery().moodCategories()).containsExactly("국", "찌개");
            assertThat(recommended.recipeQuery().moodCategories()).containsExactly("국", "찌개");
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

        @Test
        @DisplayName("일반 대화·레시피 자세히면 맛·분류 단어로 조건을 고치지 않고, 추천이면 지금처럼 고친다")
        void correctsConditions_onlyWhenRecommend() {
            IntentCorrector spiedCorrector = spy(newCorrector(ChatCorrectionProperties.ALL_ON));
            ChatIntentFacade spied = newFacade(ChatCorrectionProperties.ALL_ON, spiedCorrector);
            givenExtracted(IntentResult.general());
            spied.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("찌개는 어떻게 골라? 매운 거 기준은?"));
            // 물어본 레시피가 직전 카드에 있는 레시피 자세히 (없으면 그 요리 추천으로 바뀌어 보정한다)
            givenExtracted(new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                    null, "김치찌개", null, null, null));
            spied.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 김치찌개 어떻게 만들어?"),
                    new ChatContext(List.of(), null, List.of(new CardName(10L, "김치찌개"))));

            verify(spiedCorrector, never()).correctTastes(any(), any());
            verify(spiedCorrector, never()).correctCategories(any(), any());

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false, null, null, null));
            ChatIntentResolution recommend = spied.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 찌개 추천해줘"));

            assertThat(recommend.recipeQuery().moodCategories()).containsExactly("찌개");
            assertThat(recommend.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
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
        @DisplayName("AI가 뽑은 요리 이름은 메시지에 띄어쓰기와 상관없이 그대로 있을 때만 쓰고, 메시지에 없으면 비운다")
        void keepsAiDish_onlyWhenInMessage() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("튀김"), null, null, null, null,
                    List.of("김피탕", "탕수육"), "김피탕", null));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김피탕 같은 요리 추천해줘"))
                    .recipeQuery().keywordText()).isEqualTo("김피탕");

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null,
                    List.of("장어 조림", "장어구이"), "장어 조림", null));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("장어조림 먹고 싶어"))
                    .recipeQuery().keywordText()).isEqualTo("장어 조림");

            // AI가 키워드로 늘린 이름을 요리 이름으로 내면 메시지에 없으므로 비운다
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null,
                    List.of("냉채", "냉국"), "냉채", "차가운 요리"));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("차가운 요리 추천해줘"))
                    .recipeQuery().keywordText()).isNull();

            // AI가 요리 이름을 비우면 키워드가 메시지에 있어도 요리 이름은 없다 (서버가 추측하지 않는다)
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("찌개"), null, null, null, null,
                    List.of("김치찌개"), "", null));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치찌개 먹고 싶어"))
                    .recipeQuery().keywordText()).isNull();
        }

        @Test
        @DisplayName("\"디저트\"만 말해 AI가 키워드에 디저트를 넣어도 분류로만 쓰고 키워드·요리 이름에서 뺀다")
        void removesCategoryKeyword_whenNameOnlyCategory() {
            when(recipeService.getClassifications())
                    .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "디저트", "찌개")));
            givenExtracted(new IntentResult(ChatIntent.GENERAL, null, null, null, null, null, null, List.of("디저트"), null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트")).recipeQuery();

            assertThat(query.moodCategories()).containsExactly("디저트");
            assertThat(query.keywords()).isEmpty();
            assertThat(query.keywordText()).isNull();
        }

        @Test
        @DisplayName("\"디저트 추천해줘\"에서 AI가 키워드·요리 이름에 분류 이름을 넣어도 분류 이름은 키워드에서 빼고 요리 이름은 비운다")
        void removesMixedCategoryKeyword() {
            when(recipeService.getClassifications())
                    .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "디저트", "찌개")));
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("디저트"), null, null, null, null,
                    List.of("디저트", "푸딩", "케이크"), "디저트", null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트 추천해줘")).recipeQuery();

            assertThat(query.keywords()).containsExactly("푸딩", "케이크");
            assertThat(query.keywordText()).isNull();
        }

        @Test
        @DisplayName("\"국물\"처럼 묶음 말만 말하면 묶음의 분류로 바꾸고 키워드·요리 이름에서 뺀다")
        void removesGroupWordKeyword() {
            givenExtracted(new IntentResult(ChatIntent.GENERAL, null, null, null, null, null, null, List.of("국물"), null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국물")).recipeQuery();

            assertThat(query.moodCategories()).containsExactlyInAnyOrder("국", "찌개");
            assertThat(query.keywords()).isEmpty();
            assertThat(query.keywordText()).isNull();
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

        @Test
        @DisplayName("\"차가운 요리\"에서 AI가 키워드에 분류 이름 샐러드를 넣으면 키워드에서 빼고 분류로 옮긴다")
        void movesCategoryKeywordToCategories() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, null, null, null,
                    List.of("샐러드", "냉채", "냉국"), null, "차가운 요리"));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("차가운 요리 추천해줘")).recipeQuery();

            assertThat(query.moodCategories()).containsExactly("샐러드");
            assertThat(query.keywords()).containsExactly("냉채", "냉국");
            assertThat(query.keywordText()).isNull();
        }

        @Test
        @DisplayName("묶음 말 키워드(국물)는 묶음의 분류로 옮기고, 이번 말에서 피하는 분류(\"디저트 말고\")인 키워드는 옮기지 않고 뺀다")
        void movesGroupKeyword_butNotAvoidedCategory() {
            when(recipeService.getClassifications())
                    .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "디저트", "찌개", "볶음")));
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("찌개"), null, null, null, null,
                    List.of("국물", "김치찌개"), null, null));
            RecipeQuery grouped = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치찌개 같은 거 추천해줘"))
                    .recipeQuery();

            assertThat(grouped.moodCategories()).containsExactly("찌개", "국");
            assertThat(grouped.keywords()).containsExactly("김치찌개");

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, null, null, null,
                    List.of("디저트", "볶음밥"), null, null));
            RecipeQuery avoided = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트 말고 볶음밥 같은 거"))
                    .recipeQuery();

            assertThat(avoided.moodCategories()).isEmpty();
            assertThat(avoided.keywords()).containsExactly("볶음밥");
        }

        @Test
        @DisplayName("새 추천에서 짠맛·순한맛·감칠맛도 맛 단어로 원하는 맛과 피할 맛을 바로잡는다 (짠 거 말고 담백한 거)")
        void correctsAllTastes_whenAiMixesUp() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SALTY"), null, null, null, null, null, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("짠 거 말고 담백한 거"))
                    .recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.MILD);
            assertThat(query.avoidTastes()).containsExactly(Taste.SALTY);
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
        @DisplayName("김피탕 추천 뒤 \"디저트 추천해줘\"에 AI가 튀김·김피탕 키워드를 이어 와도 분류 디저트만 쓰고 키워드·요리 이름은 이어 가지 않는다")
        void usesDessertOnly_whenAiDragsPreviousConditions() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("튀김"), null, null, null, null,
                    List.of("김피탕", "탕수육", "깐풍기"), "김피탕", null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("디저트 추천해줘"), afterKimpitang);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().moodCategories()).containsExactly("디저트");
            assertThat(resolution.recipeQuery().keywords()).isEmpty();
            assertThat(resolution.recipeQuery().keywordText()).isNull();
        }

        @Test
        @DisplayName("김피탕 추천 뒤 \"디저트 추천해줘\"를 AI가 다시 추천으로 보고 분류를 비워도 분류 디저트로 쓰고 직전 키워드를 이어 가지 않는다")
        void usesDessertWithoutPreviousKeywords_whenAiRefinesWithEmptyCategory() {
            givenExtracted(new IntentResult(ChatIntent.REFINE, null, List.of(), null, null, null, null, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트 추천해줘"), afterKimpitang)
                    .recipeQuery();

            assertThat(query.moodCategories()).containsExactly("디저트");
            assertThat(query.keywords()).isEmpty();
        }

        @Test
        @DisplayName("디저트 추천 뒤 같은 말 \"디저트 추천해줘\"에 AI가 조건을 모두 비워도 분류 디저트로 채운다")
        void fillsDessert_whenAiEmptiesSameRequest() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, null, null, null, List.of(), null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트 추천해줘"), afterDessert)
                    .recipeQuery();

            assertThat(query.moodCategories()).containsExactly("디저트");
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
        @DisplayName("디저트 추천 뒤 \"디저트 말고 국\"은 AI가 디저트를 남겨도 분류 국으로 바꾼다")
        void changesToSoup_whenDessertAvoided() {
            givenExtracted(new IntentResult(ChatIntent.REFINE, null, List.of("디저트", "국"), null, null, null, null, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트 말고 국"), afterDessert)
                    .recipeQuery();

            assertThat(query.moodCategories()).containsExactly("국");
        }

        @Test
        @DisplayName("\"디저트 말고 다른 거\"는 AI가 디저트를 넣어도 분류에 넣지 않고 직전 분류 디저트도 이어 가지 않는다")
        void dropsDessert_whenOnlyAvoided() {
            givenExtracted(new IntentResult(ChatIntent.REFINE, null, List.of("디저트"), null, null, null, null, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("디저트 말고 다른 거"), afterDessert)
                    .recipeQuery();

            assertThat(query.moodCategories()).isEmpty();
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

        @Test
        @DisplayName("이번 말로 AI가 늘린 키워드는 분류를 채워도 그대로 둔다 (\"냉면 같은 면류 추천해줘\")")
        void keepsExpandedKeywords_whenCategoryFilled() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, null, null, null,
                    List.of("냉면", "막국수"), "냉면", null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("냉면 같은 면류 추천해줘"),
                    afterKimpitang).recipeQuery();

            assertThat(query.moodCategories()).containsExactly("면류");
            assertThat(query.keywords()).containsExactly("냉면", "막국수");
            assertThat(query.keywordText()).isEqualTo("냉면");
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
        @DisplayName("AI가 일반 대화로 봐도 냉장고 보기 말이면 냉장고 보기로 바꾸고 밀키트 단어로 범위를 MEALKIT으로 정한다 (밀키트 뭐 남았어)")
        void changesGeneralToFridge_whenFridgeWord() {
            givenExtracted(intentOnly(ChatIntent.GENERAL));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("밀키트 뭐 남았어"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.MEALKIT);
        }

        @Test
        @DisplayName("\"내 냉장고에서 밀키트 어떤 거 있어?\"는 살 밀키트 추천이 아니라 냉장고 밀키트 보기다")
        void resolvesFridgeMealkit_notMealkitRecommend() {
            givenExtracted(intentOnly(ChatIntent.GENERAL));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("내 냉장고에서 밀키트 어떤 거 있어?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.MEALKIT);
        }

        @Test
        @DisplayName("AI가 추천으로 봐도 냉장고 보기 말이면 바꾸고 AI가 뽑은 재료를 쓴다 (두부 아직 있어?)")
        void changesRecommendToFridge_withAiIngredients() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of("두부"), List.of(), false,
                    null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 아직 있어?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.lookup().names()).containsExactly("두부");
            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.ALL);
        }

        @Test
        @DisplayName("곧 버려야 할 것을 물으면 범위를 IMMINENT로 정한다")
        void picksImminentScope() {
            givenExtracted(intentOnly(ChatIntent.GENERAL));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("곧 버려야 할 거 있어?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.IMMINENT);
        }

        @Test
        @DisplayName("AI가 추천으로 봐도 \"조미료 뭐있어?\"는 냉장고 기본 양념 보기로 바꾼다")
        void changesRecommendToSeasoningFridge() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of("조미료"), List.of(), false,
                    null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("조미료 뭐있어?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.SEASONING);
            assertThat(resolution.lookup().names()).isEmpty();
        }

        @Test
        @DisplayName("AI가 상관없는 질문으로 봐도 음식 말이 있고 냉장고 보기 말이면 냉장고 보기로 되살린다 (냉장고에 뭐 있어)")
        void revivesOffTopicAsFridge() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("냉장고에 뭐 있어"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.declines()).isFalse();
        }

        @Test
        @DisplayName("AI가 냉장고 보기로 봐도 요리 말이 있으면 추천으로 둔다 (두부 있으면 두부 요리 추천해줘)")
        void keepsRecommend_whenCookWordWithFridgeIntent() {
            givenExtracted(fridge(List.of("두부"), "ALL"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("두부 있으면 두부 요리 추천해줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery()).isNotNull();
        }

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

        @Test
        @DisplayName("AI가 냉장고 보기·사기로 봐도 묻는 말이 있으면 일반 답으로, \"얼마나\"·\"가격\"은 그대로 둔다")
        void changesToGeneral_whenGeneralQuestionWithFridgeOrShop() {
            Map<String, IntentResult> general = new LinkedHashMap<>();
            general.put("유통기한 지난 거 먹어도 돼?", fridge(List.of(), "IMMINENT"));
            general.put("냉장고에서 꺼낸 고기 어떻게 해동해?", fridge(List.of("고기"), "ALL"));
            general.put("주문한 거 언제 와?", intentOnly(ChatIntent.SHOP));
            general.forEach((message, ai) -> {
                givenExtracted(ai);
                assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message)).intent())
                        .as(message).isEqualTo(ChatIntent.GENERAL);
            });

            givenExtracted(fridge(List.of("두부"), "ALL"));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 얼마나 남았어?")).intent())
                    .isEqualTo(ChatIntent.FRIDGE);
            givenExtracted(intentOnly(ChatIntent.SHOP));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 가격 얼마야?")).intent())
                    .isEqualTo(ChatIntent.SHOP);
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
        @Test
        @DisplayName("AI가 추천으로 봐도 사기 말이면 사기로 바꾸고 AI가 뽑은 재료를 쓴다 (당근 사고 싶어)")
        void changesRecommendToShop() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of("당근"), List.of(), false,
                    null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("당근 사고 싶어"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.SHOP);
            assertThat(resolution.recommends()).isFalse();
            assertThat(resolution.lookup().names()).containsExactly("당근");
        }

        @Test
        @DisplayName("AI가 일반 대화로 보고 재료를 비웠으면 서버가 말에서 재료 사전 이름을 찾는다 (대파 장바구니에 담아줘)")
        void changesGeneralToShop_withServerNames() {
            givenExtracted(new IntentResult(ChatIntent.GENERAL, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null));
            when(ingredientMatcher.matchAll(anyList())).thenReturn(Map.of("대파", ingredient(30L, "ING_PA", "대파")));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("대파 장바구니에 담아줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.SHOP);
            assertThat(resolution.lookup().names()).containsExactly("대파");
        }

        @Test
        @DisplayName("AI가 상관없는 질문으로 봐도 재료 이름이 있고 사기 말이면 사기로 되살린다")
        void revivesOffTopicAsShop() {
            givenExtracted(new IntentResult(ChatIntent.OFF_TOPIC, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null));
            when(ingredientMatcher.matchAll(anyList())).thenReturn(Map.of("당근", ingredient(31L, "ING_CARROT", "당근")));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("당근 사고 싶어"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.SHOP);
        }

        @Test
        @DisplayName("할인 질문은 일반 대화로 봐도 사기로 바꾸고, 밀키트·재료 단어로 종류를 나누며 \"재료\"는 찾을 이름으로 보지 않는다")
        void resolvesSaleQuestion_withProductType() {
            Map<String, ProductType> cases = new LinkedHashMap<>();
            cases.put("지금 세일하는 밀키트 있어?", ProductType.MEALKIT);
            cases.put("지금 세일하는 재료 있어?", ProductType.INGREDIENT);
            cases.put("지금 세일하는 거 있어?", null);
            cases.forEach((message, type) -> {
                givenExtracted(new IntentResult(ChatIntent.GENERAL, List.of(), List.of(), 0, List.of("재료"), List.of(), false,
                        null, null, null));

                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message));

                assertThat(resolution.intent()).as(message).isEqualTo(ChatIntent.SHOP);
                assertThat(resolution.lookup().saleOnly()).as(message).isTrue();
                assertThat(resolution.lookup().productType()).as(message).isEqualTo(type);
                assertThat(resolution.lookup().names()).as(message).isEmpty();
            });
        }

        @Test
        @DisplayName("AI가 사기로 봐도 요리 말이 있으면 추천으로 둔다 (두부 사서 만들 요리 추천해줘)")
        void keepsRecommend_whenCookWordWithShopIntent() {
            givenExtracted(new IntentResult(ChatIntent.SHOP, List.of(), List.of(), 0, List.of("두부"), List.of(), false, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 사서 만들 요리 추천해줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
        }
    }

    @Test
    @DisplayName("AI가 \"찌개\"를 분류 없이 키워드로만 줘도 \"찌개 끓이고 싶어\"면 분류 단어 보정이 찌개를 채운다 (평가 R14)")
    void fillsCategory_whenBoilingStew() {
        givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of(), null, List.of("두부"), null, false,
                List.of("찌개"), null, null));

        RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부도 넣어서 찌개 끓이고 싶어"))
                .recipeQuery();

        assertThat(query.moodCategories()).containsExactly("찌개");
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

        @Test
        @DisplayName("분류 단어 보정을 끄면 AI 분류를 그대로 쓴다")
        void keepsAiCategories_whenCategoryWordsOff() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, List.of("국"), null, null, null, false, null, null, null));
            ChatMessageCreateRequest request = ChatMessageCreateRequest.of("찌개 추천해줘");

            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, request).recipeQuery().moodCategories()).containsExactly("찌개");
            assertThat(newFacade(off("category-words")).resolve(MEMBER_ID, SESSION_ID, request).recipeQuery().moodCategories())
                    .containsExactly("국");
        }

        @Test
        @DisplayName("OFF_TOPIC 되살리기를 끄면 음식 말이 있어도 AI대로 거절한다")
        void keepsOffTopic_whenReviveOff() {
            givenExtracted(new IntentResult(ChatIntent.OFF_TOPIC, null, null, null, null, null, false, null, null, null));
            ChatMessageCreateRequest request = ChatMessageCreateRequest.of("저녁 먹을 거 추천해줘");

            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, request).intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(newFacade(off("off-topic-revive")).resolve(MEMBER_ID, SESSION_ID, request).intent())
                    .isEqualTo(ChatIntent.OFF_TOPIC);
        }

        @Test
        @DisplayName("요리 이름 확인을 끄면 메시지에 없는 AI 요리 이름도 그대로 쓴다")
        void keepsAiDish_whenDishCheckOff() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false, List.of("냉채", "냉국"), "냉채", null));
            ChatMessageCreateRequest request = ChatMessageCreateRequest.of("차가운 요리 추천해줘");

            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, request).recipeQuery().keywordText()).isNull();
            assertThat(newFacade(off("dish-check")).resolve(MEMBER_ID, SESSION_ID, request).recipeQuery().keywordText())
                    .isEqualTo("냉채");
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
