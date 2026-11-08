package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCategoryWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatFoodWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteOppositeProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.DetailQuestion;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RefineCondition;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatIntentFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TOFU = 21L;
    private static final Long MUSHROOM = 161L;
    private static final Long CUCUMBER = 15L;

    // config/chat-words.yml app.chat.taste-opposites와 같은 쌍
    static final ChatTasteOppositeProperties TASTE_OPPOSITES = new ChatTasteOppositeProperties(List.of(
            List.of(Taste.MILD, Taste.SPICY), List.of(Taste.MILD, Taste.SALTY)));

    // config/chat-words.yml app.chat.food-words와 같은 목록
    static final ChatFoodWordProperties FOOD_WORDS = new ChatFoodWordProperties(List.of("먹", "요리", "레시피", "밥", "반찬",
            "끼니", "아침", "점심", "저녁", "야식", "간식", "재료", "냉장고", "장보기", "밀키트"));

    private ChatIntentExtractor extractor;
    private RecipeService recipeService;
    private IngredientMatcher ingredientMatcher;
    private ChatMessageService chatMessageService;
    private AllergyExclusionFinder allergyExclusionFinder;
    private ChatIntentFacade facade;

    @BeforeEach
    void setUp() {
        extractor = mock(ChatIntentExtractor.class);
        recipeService = mock(RecipeService.class);
        ingredientMatcher = mock(IngredientMatcher.class);
        chatMessageService = mock(ChatMessageService.class);
        allergyExclusionFinder = mock(AllergyExclusionFinder.class);
        when(allergyExclusionFinder.find(any(), any())).thenReturn(AllergyExclusionFinder.Allergens.NONE);
        facade = new ChatIntentFacade(extractor, recipeService, ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                chatMessageService, TasteWordCheckerTest.WORDS, MealkitWordCheckerTest.WORDS, AskWordCheckerTest.WORDS,
                new ChatCategoryWordProperties(CategoryWordCheckerTest.GROUPS), FOOD_WORDS, allergyExclusionFinder, TASTE_OPPOSITES,
                FridgeShopWordCheckerTest.WORDS);
        when(recipeService.getClassifications())
                .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "기타", "샐러드", "양념", "음료", "찌개")));
    }

    @Nested
    @DisplayName("칩 (mode=RECOMMEND)")
    class RecommendMode {

        @Test
        @DisplayName("mode=RECOMMEND면 AI를 부르지 않고 선택 재료와 선택 재료만 쓰기 요청값으로 추천한다")
        void recommendsWithoutExtractor_whenModeIsRecommend() {
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(
                    "두부, 버섯으로 추천해줘", ChatMessageMode.RECOMMEND, List.of(TOFU, MUSHROOM), true);

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, request);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.source()).isEqualTo(Source.MODE);
            assertThat(resolution.recipeQuery()).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of(TOFU, MUSHROOM), true));
            verify(extractor, never()).extract(any(), any(), any(), any(), any());
            verify(ingredientMatcher, never()).matchAll(anyList());
        }
    }

    @Nested
    @DisplayName("AI 판단 (mode=AUTO)")
    class AutoMode {

        @Test
        @DisplayName("말하지 않은 조건이 시간 0·빈 목록·false로 오면 시간은 null, 나머지는 조건 없음으로 만든다")
        void leavesConditionsEmpty_whenAiReturnsZeroAndEmptyLists() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("뭐 먹지?")).recipeQuery();

            assertThat(query).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of()));
            assertThat(query.maxMinutes()).isNull();
            verify(ingredientMatcher, never()).matchAll(anyList());
        }

        @Test
        @DisplayName("AI가 RECOMMEND라고 하면 정리한 맛·분류·시간으로 검색 조건을 만든다")
        void buildsRecipeQuery_whenIntentIsRecommend() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND,
                    List.of("SPICY", "BITTER"), List.of("국", "디저트"), 30, null, null, null, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 국"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.source()).isEqualTo(Source.AI);
            RecipeQuery query = resolution.recipeQuery();
            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.moodCategories()).containsExactly("국");
            assertThat(query.maxMinutes()).isEqualTo(30);
            assertThat(query.useSelectedOnly()).isFalse();
        }

        @Test
        @DisplayName("AI에 넘기는 분류 목록은 레시피 분류에서 후보 제외 분류(양념·음료)와 기타를 뺀 것이다")
        void passesCategoriesWithoutExcluded() {
            givenExtracted(IntentResult.general());

            facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("뭐 먹지?"));

            verify(extractor).extract(MEMBER_ID, SESSION_ID, "뭐 먹지?", List.of("국", "샐러드", "찌개"), null);
        }

        @Test
        @DisplayName("AI가 GENERAL이라고 하면 검색 조건 없이 일반 답으로 정한다")
        void returnsGeneral_whenIntentIsGeneral() {
            givenExtracted(IntentResult.general());

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("두부 보관 어떻게 해?", ChatMessageMode.AUTO, List.of(TOFU), false));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(Source.AI);
            assertThat(resolution.recipeQuery()).isNull();
        }

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
        @DisplayName("묻는 말, 음식 말이 없는 말(안녕), 긴 말은 이름만 말한 말로 보지 않고 일반 답 그대로 둔다")
        void keepsGeneral_whenNotNameOnly() {
            givenExtracted(IntentResult.general());

            for (String message : List.of("밀키트 추천 기준이 뭐야?", "안녕", "오늘 날씨가 너무 좋아서 기분이 정말 좋아")) {
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
            ChatIntentFacade spied = spy(facade);
            givenExtracted(IntentResult.general());
            spied.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("찌개는 어떻게 골라? 매운 거 기준은?"));
            givenExtracted(new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                    null, "김치찌개", null, null, null));
            spied.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 김치찌개 어떻게 만들어?"));

            verify(spied, never()).correctTastes(any(), any(), any(), any());
            verify(spied, never()).correctCategories(any(), any(), any(), any(), any(), any());

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false, null, null, null));
            ChatIntentResolution recommend = spied.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 찌개 추천해줘"));

            assertThat(recommend.recipeQuery().moodCategories()).containsExactly("찌개");
            assertThat(recommend.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
        }

        @Test
        @DisplayName("AI 호출이 실패하면 선택 재료가 있어도 묻는 말(두부 얼마나 보관돼?)은 일반 답으로 둔다")
        void returnsGeneral_whenExtractorFailsWithSelectedAndQuestion() {
            when(extractor.extract(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("timeout"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("두부 얼마나 보관돼?", ChatMessageMode.AUTO, List.of(TOFU), false));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(Source.FALLBACK);
        }

        @Test
        @DisplayName("AI 호출이 실패하고 선택 재료가 있으면 선택 재료로 추천한다")
        void recommendsWithSelected_whenExtractorFailsWithSelected() {
            when(extractor.extract(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("timeout"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("이걸로", ChatMessageMode.AUTO, List.of(TOFU), true));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.source()).isEqualTo(Source.FALLBACK);
            assertThat(resolution.recipeQuery()).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of(TOFU), true));
        }

        @Test
        @DisplayName("AI 호출이 실패하고 선택 재료가 없으면 일반 답으로 정한다")
        void returnsGeneral_whenExtractorFailsWithoutSelected() {
            when(extractor.extract(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("broken json"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("뭐 먹지?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(Source.FALLBACK);
            assertThat(resolution.recipeQuery()).isNull();
        }

        @Test
        @DisplayName("분류 목록을 못 읽어도 실패로 보고 선택 재료 유무로 정한다")
        void returnsGeneral_whenCategoriesFail() {
            when(recipeService.getClassifications()).thenThrow(new RuntimeException("db down"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("뭐 먹지?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.source()).isEqualTo(Source.FALLBACK);
            verify(extractor, never()).extract(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("선택 재료만 쓰기는 요청값이 true면 AI가 false라고 해도 true다")
        void keepsUseSelectedOnly_whenRequestIsTrue() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("추천해줘", ChatMessageMode.AUTO, List.of(TOFU), true));

            assertThat(resolution.recipeQuery().useSelectedOnly()).isTrue();
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
    }

    @Nested
    @DisplayName("재료 이름")
    class IngredientNames {

        @Test
        @DisplayName("말한 재료 이름을 재료 ID로 바꾸고, 못 찾은 이름(ING_CUSTOM)은 버린다")
        void mapsNamesToIds_andDropsCustom() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null,
                    List.of("두부", "용과즙"), List.of("오이"), true, null, null, null));
            when(ingredientMatcher.matchAll(List.of("두부", "용과즙", "오이"))).thenReturn(Map.of(
                    "두부", ingredient(TOFU, "ING_TOFU", "두부"),
                    "용과즙", ingredient(999L, Ingredient.ING_CUSTOM, "기타"),
                    "오이", ingredient(CUCUMBER, "ING_CUCUMBER", "오이")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("두부랑 용과즙으로 할 수 있는 거, 오이는 빼고")).recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(TOFU);
            assertThat(query.excludeIngredientIds()).containsExactly(CUCUMBER);
            assertThat(query.useSelectedOnly()).isTrue();
        }

        @Test
        @DisplayName("\"두부도 넣어서\"처럼 AI가 선택 재료만 쓰기를 false로 주면 재료는 담되 false로 둔다")
        void keepsUseSelectedOnlyFalse_whenIngredientIsOptional() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), null, null,
                    List.of("두부"), null, false, null, null, null));
            when(ingredientMatcher.matchAll(List.of("두부"))).thenReturn(Map.of("두부", ingredient(TOFU, "ING_TOFU", "두부")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부도 넣어서 매운 거"))
                    .recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(TOFU);
            assertThat(query.useSelectedOnly()).isFalse();
        }

        @Test
        @DisplayName("칩 없이 말로만 재료를 정하면 그 재료가 선택 재료가 된다")
        void usesSpokenIngredientsAsSelected_whenNoChip() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, List.of("버섯", "두부"), null, true, null, null, null));
            when(ingredientMatcher.matchAll(List.of("버섯", "두부"))).thenReturn(Map.of(
                    "버섯", ingredient(MUSHROOM, "ING_MUSHROOM", "표고버섯"),
                    "두부", ingredient(TOFU, "ING_TOFU", "두부")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("버섯 두부로 할 수 있는 요리 추천해줘")).recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(MUSHROOM, TOFU);
            assertThat(query.useSelectedOnly()).isTrue();
        }

        @Test
        @DisplayName("칩으로 고른 재료와 말한 재료를 합치고 중복은 뺀다 (고른 재료가 앞)")
        void mergesChipAndSpokenIngredients_withoutDuplicates() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, List.of("두부", "버섯"), null, null, null, null, null));
            when(ingredientMatcher.matchAll(List.of("두부", "버섯"))).thenReturn(Map.of(
                    "두부", ingredient(TOFU, "ING_TOFU", "두부"),
                    "버섯", ingredient(MUSHROOM, "ING_MUSHROOM", "표고버섯")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("두부랑 버섯도 넣어서", ChatMessageMode.AUTO, List.of(CUCUMBER, TOFU), false))
                    .recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(CUCUMBER, TOFU, MUSHROOM);
            assertThat(query.useSelectedOnly()).isFalse();
        }

        @Test
        @DisplayName("빼달라고 한 재료는 고른 재료에 있어도 선택 재료에서 뺀다")
        void removesExcludedFromSelected() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, List.of("오이"), null, null, null, null));
            when(ingredientMatcher.matchAll(List.of("오이"))).thenReturn(Map.of("오이", ingredient(CUCUMBER, "ING_CUCUMBER", "오이")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("오이는 빼고", ChatMessageMode.AUTO, List.of(CUCUMBER, TOFU), false))
                    .recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(TOFU);
            assertThat(query.excludeIngredientIds()).containsExactly(CUCUMBER);
        }

        @Test
        @DisplayName("재료 이름 매칭이 실패하면 재료 조건 없이 나머지 조건으로 추천한다")
        void dropsIngredients_whenMatcherFails() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), null, null,
                    List.of("두부"), null, null, null, null, null));
            when(ingredientMatcher.matchAll(eq(List.of("두부")))).thenThrow(new RuntimeException("es down"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부로 매운 거"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().selectedIngredientIds()).isEmpty();
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
        }
    }

    @Nested
    @DisplayName("이어지는 말 (직전 추천이 있을 때)")
    class FollowUp {

        private final LastRecommendation lastRecommendation = LastRecommendation.of(
                new RecipeQuery(MEMBER_ID, List.of(TOFU), List.of(Taste.SPICY), List.of("튀김"), null, null, false,
                        List.of("김피탕", "탕수육"), "김피탕", "김피탕 같은 요리"),
                List.of(10L, 20L, 30L));
        private final List<CardName> lastCards = List.of(
                new CardName(10L, "탕수육"), new CardName(20L, "깐풍기"), new CardName(30L, "돼지고기 찹쌀부침"));
        private final ChatContext context = new ChatContext(List.of(
                new ContextMessage(ChatMessageRole.USER, "두부 요리", null),
                new ContextMessage(ChatMessageRole.ASSISTANT, "골랐어요.", List.of(new CardName(5L, "두부조림"))),
                new ContextMessage(ChatMessageRole.USER, "김피탕 같은 요리", null),
                new ContextMessage(ChatMessageRole.ASSISTANT, "비슷한 걸 골랐어요.", lastCards)),
                lastRecommendation, lastCards);

        @Test
        @DisplayName("직전 추천이 없으면(첫 질문) 의도 판단에 직전 추천 정보를 넘기지 않는다")
        void passesNoFollowUp_whenNoLastCards() {
            givenExtracted(IntentResult.general());

            facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("뭐 먹지?"), ChatContext.empty());

            verify(extractor).extract(eq(MEMBER_ID), eq(SESSION_ID), eq("뭐 먹지?"), anyList(), isNull());
        }

        @Test
        @DisplayName("직전 추천이 있으면 직전 카드 번호·이름, 최근 대화의 다른 카드, 직전 조건을 줄 글로 넘긴다")
        void passesFollowUpLines_whenLastCardsExist() {
            givenExtracted(IntentResult.general());

            facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("고마워"), context);

            verify(extractor).extract(eq(MEMBER_ID), eq(SESSION_ID), eq("고마워"), anyList(), eq("""
                    [직전 추천] 1. 탕수육 / 2. 깐풍기 / 3. 돼지고기 찹쌀부침
                    [이전 추천] 두부조림
                    [직전 조건] 원하는 맛 SPICY / 분류 튀김 / 키워드 김피탕, 탕수육"""));
        }

        @Test
        @DisplayName("\"두 번째 거\"는 직전 카드 2번 레시피를 자세히 답한다")
        void picksSecondLastCard_whenTargetIndexIsTwo() {
            givenExtracted(detail(2, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("두 번째 거 어떻게 만들어?"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
            assertThat(resolution.targetRecipeId()).isEqualTo(20L);
            verify(chatMessageService, never()).findRecentRecommendedRecipeIds(any(), anyInt());
        }

        @Test
        @DisplayName("\"아까 그 깐풍기\"처럼 직전 카드에 없는 이름은 상담 전체 추천(최근부터)에서 띄어쓰기 없이 이름으로 찾는다")
        void findsByNameInSessionRecommendations_whenNotInLastCards() {
            ChatContext afterOtherRecommendation = new ChatContext(List.of(), lastRecommendation,
                    List.of(new CardName(40L, "두부조림"), new CardName(50L, "버섯전")));
            givenExtracted(detail(null, "깐풍기"));
            when(chatMessageService.findRecentRecommendedRecipeIds(List.of(SESSION_ID), 30))
                    .thenReturn(Map.of(SESSION_ID, List.of(40L, 50L, 20L)));
            when(recipeService.findSummaries(List.of(40L, 50L, 20L))).thenReturn(Map.of(
                    40L, new RecipeSummary(40L, "두부조림", null, null), 50L, new RecipeSummary(50L, "버섯전", null, null),
                    20L, new RecipeSummary(20L, "매콤 깐풍 기", null, null)));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("아까 그 깐풍기 시간 얼마나 걸려?"), afterOtherRecommendation);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
            assertThat(resolution.targetRecipeId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("띄어 쓴 이름(\"장어 조림\")도 붙여 쓴 레시피 이름(\"장어조림\")을 상담 전체 추천에서 찾는다")
        void findsSpacedName_againstJoinedRecipeName() {
            givenExtracted(detail(null, "장어 조림"));
            givenSessionRecipes(Map.of(60L, "장어조림", 61L, "두부조림"), List.of(61L, 60L));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("아까 그 장어 조림 자세히"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECIPE_DETAIL);
            assertThat(resolution.targetRecipeId()).isEqualTo(60L);
        }

        @Test
        @DisplayName("붙여 쓴 이름(\"장어조림\")도 띄어 쓴 레시피 이름(\"장어 조림\")을 찾고, 직전 카드에 있으면 직전 카드에서 찾는다")
        void findsJoinedName_againstSpacedRecipeName() {
            givenExtracted(detail(null, "장어조림"));
            givenSessionRecipes(Map.of(60L, "장어 조림"), List.of(60L));

            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("아까 그 장어조림 자세히"), context)
                    .targetRecipeId()).isEqualTo(60L);

            ChatContext spacedInLast = new ChatContext(List.of(), lastRecommendation, List.of(new CardName(70L, "돼지고기 찹쌀 부침")));
            givenExtracted(detail(null, "돼지고기찹쌀부침"));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("돼지고기찹쌀부침 어떻게 해?"), spacedInLast)
                    .targetRecipeId()).isEqualTo(70L);
        }

        private void givenSessionRecipes(Map<Long, String> names, List<Long> recentFirst) {
            when(chatMessageService.findRecentRecommendedRecipeIds(List.of(SESSION_ID), 30))
                    .thenReturn(Map.of(SESSION_ID, recentFirst));
            Map<Long, RecipeSummary> summaries = new HashMap<>();
            names.forEach((id, name) -> summaries.put(id, new RecipeSummary(id, name, null, null)));
            when(recipeService.findSummaries(recentFirst)).thenReturn(summaries);
        }

        @Test
        @DisplayName("물어본 레시피를 직전 카드에서도 상담 전체 추천에서도 못 찾으면 일반 답으로 한다")
        void answersGeneral_whenTargetNotFound() {
            givenExtracted(detail(5, "마파두부"));
            when(chatMessageService.findRecentRecommendedRecipeIds(List.of(SESSION_ID), 30))
                    .thenReturn(Map.of(SESSION_ID, List.of(10L)));
            when(recipeService.findSummaries(List.of(10L))).thenReturn(Map.of(10L, new RecipeSummary(10L, "탕수육", null, null)));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("마파두부 어떻게 해?"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.targetRecipeId()).isNull();
        }

        @Test
        @DisplayName("레시피 자세히 답 뒤에도 \"첫 번째 거는?\"은 최근 대화의 카드 하나가 아니라 직전 추천(Redis) 카드 1번으로 잡는다")
        void picksFromLastRecommendation_afterRecipeDetail() {
            ChatContext afterDetail = new ChatContext(List.of(
                    new ContextMessage(ChatMessageRole.USER, "두 번째 거 어떻게 만들어?", null),
                    new ContextMessage(ChatMessageRole.ASSISTANT, "깐풍기는 이렇게 만들어요.", List.of(new CardName(20L, "깐풍기")))),
                    lastRecommendation, lastCards);
            givenExtracted(detail(1, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("첫 번째 거는?"), afterDetail);

            assertThat(resolution.targetRecipeId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("다시 추천인데 직전 조건이 없으면(Redis가 비었음) 이번 말만으로 새 추천을 한다")
        void recommendsNew_whenRefineWithoutLastRecommendation() {
            ChatContext withoutRedis = new ChatContext(context.recentMessages(), null, lastCards);
            givenExtracted(new IntentResult(ChatIntent.REFINE, List.of("SPICY"), null, null, null, null, null, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("좀 더 매운 걸로"), withoutRedis);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(resolution.recipeQuery().moodCategories()).isEmpty();
        }

        @Test
        @DisplayName("다시 추천이고 직전 조건이 있으면 다시 추천으로 정한다")
        void resolvesRefine_whenLastRecommendationExists() {
            givenExtracted(new IntentResult(ChatIntent.REFINE, List.of("SPICY"), List.of("튀김"), null, null, null, null,
                    null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    ChatMessageCreateRequest.of("좀 더 매운 걸로"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.REFINE);
            assertThat(resolution.recommends()).isTrue();
        }

        @Test
        @DisplayName("레시피 자세히의 질문 종류를 넘긴다 (두 번째 거 어떻게 만들어 → HOW_TO, 시간 → TIME, 재료 → INGREDIENTS)")
        void passesDetailQuestion() {
            assertThat(resolveDetail("두 번째 거 어떻게 만들어?", detail(2, null, "HOW_TO")).detailQuestion())
                    .isEqualTo(DetailQuestion.HOW_TO);
            assertThat(resolveDetail("첫 번째 거는 시간 얼마나 걸려?", detail(1, null, "TIME")).detailQuestion())
                    .isEqualTo(DetailQuestion.TIME);
            assertThat(resolveDetail("그거 재료 뭐 들어가?", detail(1, null, "INGREDIENTS")).detailQuestion())
                    .isEqualTo(DetailQuestion.INGREDIENTS);
        }

        private ChatIntentResolution resolveDetail(String message, IntentResult result) {
            givenExtracted(result);
            return facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message), context);
        }

        private static IntentResult detail(Integer index, String name) {
            return detail(index, name, null);
        }

        private static IntentResult detail(Integer index, String name, String question) {
            return new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null, index, name,
                    null, null, question);
        }
    }

    @Nested
    @DisplayName("다시 추천 조건 합치기")
    class Refine {

        // 직전 조건: 두부를 꼭 쓰는 매운 국·찌개, 오이 빼기
        private final RecipeQuery previous = new RecipeQuery(MEMBER_ID, List.of(TOFU), List.of(Taste.SPICY), List.of("국", "찌개"),
                null, List.of(CUCUMBER), true, List.of("김치찌개"), "김치찌개", null);
        private final ChatContext context = new ChatContext(List.of(), LastRecommendation.of(previous, List.of(10L, 20L, 30L)),
                List.of(new CardName(10L, "김치찌개"), new CardName(20L, "순두부찌개"), new CardName(30L, "두부국")));

        @Test
        @DisplayName("\"좀 더 매운 걸로\"는 AI가 낸 맛으로 바꾸고 분류·키워드·재료 같은 나머지 조건은 직전 그대로 둔다")
        void changesTasteOnly_whenMoreSpicy() {
            givenExtracted(refine(List.of("SPICY", "SALTY"), null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("좀 더 매운 걸로"), context)
                    .recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY, Taste.SALTY);
            assertThat(query.moodCategories()).containsExactly("국", "찌개");
            assertThat(query.keywords()).containsExactly("김치찌개");
            assertThat(query.selectedIngredientIds()).containsExactly(TOFU);
            assertThat(query.excludeIngredientIds()).containsExactly(CUCUMBER);
            assertThat(query.useSelectedOnly()).isTrue();
        }

        @Test
        @DisplayName("\"국물 말고 볶음으로\"는 분류만 볶음으로 바꾸고 맛·재료는 직전 그대로 둔다")
        void changesCategoryOnly_whenStirFryInstead() {
            when(recipeService.getClassifications())
                    .thenReturn(new RecipeClassificationResponse(List.of("한식"), List.of("국", "볶음", "찌개")));
            givenExtracted(refine(null, List.of("볶음")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국물 말고 볶음으로"), context)
                    .recipeQuery();

            assertThat(query.moodCategories()).containsExactly("볶음");
            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.selectedIngredientIds()).containsExactly(TOFU);
        }

        @Test
        @DisplayName("\"다른 거 없어?\"처럼 AI 조건이 모두 비면 직전 조건을 그대로 쓴다")
        void keepsPreviousQuery_whenNothingChanged() {
            givenExtracted(refine(null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("다른 거 없어?"), context);

            assertThat(resolution.intent()).isEqualTo(ChatIntent.REFINE);
            // 요리 이름·원한 표현만 이어 가지 않고 나머지는 직전 그대로
            assertThat(resolution.recipeQuery()).isEqualTo(new RecipeQuery(MEMBER_ID, List.of(TOFU), List.of(Taste.SPICY),
                    List.of("국", "찌개"), null, List.of(CUCUMBER), true, List.of("김치찌개"), null, null));
        }

        @Test
        @DisplayName("\"맛은 상관없어\"처럼 removed에 맛이 있으면 직전 원하는 맛 조건을 지우고 나머지는 직전 그대로 둔다")
        void removesTaste_whenNotSpicy() {
            givenExtracted(refine(null, null, List.of("TASTE")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("맛은 상관없어"), context)
                    .recipeQuery();

            assertThat(query.wantedTastes()).isEmpty();
            assertThat(query.avoidTastes()).isEmpty();
            assertThat(query.moodCategories()).containsExactly("국", "찌개");
            assertThat(query.keywords()).containsExactly("김치찌개");
        }

        @Test
        @DisplayName("removed 없이 맛 칸만 비어 있으면 직전 맛을 그대로 둔다")
        void keepsTaste_whenOnlyEmptyWithoutRemoved() {
            givenExtracted(refine(List.of(), List.of("국"), List.of()));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("국으로만"), context)
                    .recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.moodCategories()).containsExactly("국");
        }

        @Test
        @DisplayName("removed의 시간·재료 조건은 직전 값을 지운다 (\"시간 상관없이\", \"두부 안 넣어도 돼\", \"오이 넣어도 돼\")")
        void removesTimeAndIngredients() {
            RecipeQuery withTime = new RecipeQuery(MEMBER_ID, List.of(TOFU), null, null, 20, List.of(CUCUMBER), true);
            RecipeQuery current = RecipeQuery.of(MEMBER_ID, List.of());

            RecipeQuery merged = ChatIntentFacade.mergeRefine(withTime, current,
                    Set.of(RefineCondition.MAX_MINUTES, RefineCondition.INCLUDE, RefineCondition.EXCLUDE));

            assertThat(merged.maxMinutes()).isNull();
            assertThat(merged.selectedIngredientIds()).isEmpty();
            assertThat(merged.useSelectedOnly()).isFalse();
            assertThat(merged.excludeIngredientIds()).isEmpty();
            assertThat(ChatIntentFacade.mergeRefine(withTime, current, Set.of()).maxMinutes()).isEqualTo(20);
        }

        @Test
        @DisplayName("다시 추천은 요리 이름·원한 표현을 이어 가지 않아 \"김피탕 레시피는 없어서\" 안내가 다시 나오지 않는다")
        void doesNotRepeatDishNotice_whenRefine() {
            RecipeQuery kimpi = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("튀김"), null, null, false,
                    List.of("김피탕", "탕수육", "깐풍기"), "김피탕", "김피탕 같은 요리");
            RecipeQuery merged = ChatIntentFacade.mergeRefine(kimpi, RecipeQuery.of(MEMBER_ID, List.of()), Set.of())
                    .withKeywordMatches(Map.of(10L, new KeywordMatch(KeywordMatch.Type.EXPANDED, "탕수육", null)));
            RecipeCandidate sweetSour = new RecipeCandidate(10L, "탕수육", "튀김", 40, new Ranking(2, 0, 0, 0, 1, 1, 0),
                    new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(), List.of(),
                            List.of(), merged.keywordMatches().get(10L)));
            RecipeQuery original = kimpi.withKeywordMatches(merged.keywordMatches());

            assertThat(merged.keywordText()).isNull();
            assertThat(merged.preferenceText()).isNull();
            assertThat(merged.keywords()).containsExactly("김피탕", "탕수육", "깐풍기");
            assertThat(RecommendationPromptBuilder.notices(merged, List.of(sweetSour), merged.keywordsNotFound())).isEmpty();
            assertThat(RecommendationPromptBuilder.notices(original, List.of(sweetSour), original.keywordsNotFound()))
                    .containsExactly("김피탕 레시피는 없어서, 비슷한 탕수육을 골랐어요");
        }

        @Test
        @DisplayName("재료는 서버가 이어 간다: 직전 선택 재료에 이번에 고른 재료를 더하고, 뺄 재료는 합쳐 선택 재료에서 뺀다")
        void carriesIngredients() {
            RecipeQuery current = new RecipeQuery(MEMBER_ID, List.of(MUSHROOM, CUCUMBER), null, null, null, List.of(TOFU), false);

            RecipeQuery merged = ChatIntentFacade.mergeRefine(previous, current, Set.of());

            assertThat(merged.selectedIngredientIds()).containsExactly(MUSHROOM);
            assertThat(merged.excludeIngredientIds()).containsExactly(CUCUMBER, TOFU);
            assertThat(merged.useSelectedOnly()).isTrue();
        }

        @Test
        @DisplayName("이번에 칩으로 고른 재료도 직전 선택 재료에 더한다")
        void addsSelectedChips() {
            givenExtracted(refine(null, null));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest("이것도 넣어서 다른 거", ChatMessageMode.AUTO,
                    List.of(MUSHROOM), false);

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, request, context).recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(TOFU, MUSHROOM);
        }

        @Test
        @DisplayName("다시 추천에서 \"매운 거 말고\"는 피할 맛 매운맛으로 두고, 직전에 원했던 매운맛은 지운다")
        void avoidsSpicy_afterMoreSpicy() {
            givenExtracted(avoid(List.of("SPICY"), List.of("SPICY")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고"), context)
                    .recipeQuery();

            assertThat(query.avoidTastes()).containsExactly(Taste.SPICY);
            assertThat(query.wantedTastes()).isEmpty();
            assertThat(query.moodCategories()).containsExactly("국", "찌개");
        }

        @Test
        @DisplayName("피할 맛은 다시 추천에서 이어 간다 (\"매운 거 말고\" 뒤 \"다른 거 없어?\"도 안 매운 걸로)")
        void carriesAvoidTastes_whenRefineAgain() {
            RecipeQuery noSpicy = previous.withAvoidTastes(List.of(Taste.SPICY));
            ChatContext afterAvoid = new ChatContext(List.of(), LastRecommendation.of(noSpicy, List.of(10L)), context.lastCards());
            givenExtracted(refine(null, null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("다른 거 없어?"), afterAvoid)
                    .recipeQuery();

            assertThat(query.avoidTastes()).containsExactly(Taste.SPICY);
            assertThat(query.wantedTastes()).isEmpty();
        }

        @Test
        @DisplayName("\"매운 거 말고\" 뒤 \"김치찌개\"는 새 추천이라 AI가 따라 넣은 피할 맛을 빼고, 이번 말에 맛이 있으면 그 맛은 남긴다")
        void dropsCarriedTastes_whenNewRecommend() {
            RecipeQuery noSpicy = previous.withAvoidTastes(List.of(Taste.SPICY));
            ChatContext afterAvoid = new ChatContext(List.of(), LastRecommendation.of(noSpicy, List.of(10L)), context.lastCards());
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SWEET"), List.of("찌개"), 0, List.of(), List.of(), false,
                    List.of("김치찌개"), "김치찌개", null, null, null, List.of(), List.of("SPICY")));

            RecipeQuery stew = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("김치찌개"), afterAvoid)
                    .recipeQuery();

            assertThat(stew.avoidTastes()).isEmpty();
            assertThat(stew.wantedTastes()).isEmpty();
            assertThat(stew.keywords()).containsExactly("김치찌개");

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), null, 0, List.of(), List.of(), false,
                    List.of(), null, null, null, null, List.of(), List.of()));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 추천해줘"), afterAvoid)
                    .recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);

            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, 0, List.of(), List.of(), false,
                    List.of("김치찌개"), "김치찌개", null, null, null, List.of(), List.of("SPICY")));
            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고 김치찌개 줘"), afterAvoid)
                    .recipeQuery().avoidTastes()).containsExactly(Taste.SPICY);
        }

        @Test
        @DisplayName("피할 맛을 이어 가다가 \"다시 매운 걸로\"처럼 그 맛을 원하면 피할 맛에서 푼다")
        void releasesAvoidTaste_whenWantedAgain() {
            RecipeQuery noSpicy = previous.withAvoidTastes(List.of(Taste.SPICY));
            ChatContext afterAvoid = new ChatContext(List.of(), LastRecommendation.of(noSpicy, List.of(10L)), context.lastCards());
            givenExtracted(refine(List.of("SPICY"), null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("다시 매운 걸로"), afterAvoid)
                    .recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.avoidTastes()).isEmpty();
        }

        @Test
        @DisplayName("\"뭐 먹지?\" 다음 \"좀 더 매운 걸로\"는 AI가 피할 맛으로 줘도 서버가 원하는 맛 매운맛으로 고친다")
        void correctsMoreSpicy_whenAiPutsItInAvoid() {
            RecipeQuery anything = RecipeQuery.of(MEMBER_ID, List.of());
            ChatContext afterAnything = new ChatContext(List.of(), LastRecommendation.of(anything, List.of(10L)), context.lastCards());
            givenExtracted(avoid(null, List.of("SPICY")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("좀 더 매운 걸로"), afterAnything)
                    .recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.avoidTastes()).isEmpty();
        }

        @Test
        @DisplayName("\"매운 거 말고\"·\"덜 맵게\"는 AI가 원하는 맛으로 줘도 서버가 피할 맛 매운맛으로 고친다")
        void correctsToAvoid_whenNegated() {
            for (String message : List.of("매운 거 말고", "덜 맵게")) {
                givenExtracted(avoid(List.of("SPICY"), null));

                RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message), context).recipeQuery();

                assertThat(query.avoidTastes()).as(message).containsExactly(Taste.SPICY);
                assertThat(query.wantedTastes()).as(message).isEmpty();
            }
        }

        @Test
        @DisplayName("\"매운 거 말고 짭짤하게\"는 피할 맛 매운맛, 원하는 맛 짠맛이다")
        void avoidsSpicyAndWantsSalty() {
            givenExtracted(avoid(List.of("SALTY"), null));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고 짭짤하게"), context)
                    .recipeQuery();

            assertThat(query.avoidTastes()).containsExactly(Taste.SPICY);
            assertThat(query.wantedTastes()).containsExactly(Taste.SALTY);
        }

        private static IntentResult avoid(List<String> tastes, List<String> avoidTastes) {
            return new IntentResult(ChatIntent.REFINE, tastes, null, null, null, null, null, null, null, null,
                    null, null, null, avoidTastes);
        }

        private static IntentResult refine(List<String> tastes, List<String> categories) {
            return refine(tastes, categories, null);
        }

        private static IntentResult refine(List<String> tastes, List<String> categories, List<String> removed) {
            return new IntentResult(ChatIntent.REFINE, tastes, categories, null, null, null, null, null, null, null,
                    null, null, removed);
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

    @Test
    @DisplayName("의도 판단에 넘기는 직전 조건 줄에 피할 맛을 쓴다")
    void writesAvoidTastesInFollowUp() {
        RecipeQuery noSpicy = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("튀김"), null, null, false)
                .withAvoidTastes(List.of(Taste.SPICY));
        ChatContext context = new ChatContext(List.of(), LastRecommendation.of(noSpicy, List.of(10L)),
                List.of(new CardName(10L, "탕수육")));

        assertThat(ChatIntentFacade.followUp(context)).isEqualTo("[직전 추천] 1. 탕수육\n[직전 조건] 피할 맛 SPICY / 분류 튀김");
    }

    @Nested
    @DisplayName("다시 추천에서 맛이 부딪히면 이번 말을 따름")
    class TasteConflict {

        @Test
        @DisplayName("\"매운 거 말고\" → \"좀 더 매운 걸로\"면 원하는 맛 SPICY, 피할 맛 없음 (AI가 직전 피할 맛을 이어 써도)")
        void wantsSpicy_whenAvoidedThenAskedSpicier() {
            RecipeQuery query = refineAfter(tastes(List.of(), List.of(Taste.SPICY)), "좀 더 매운 걸로",
                    List.of("SPICY"), List.of("SPICY"));

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.avoidTastes()).isEmpty();
        }

        @Test
        @DisplayName("\"매운 거 추천해줘\" → \"매운 거 말고\"면 피할 맛 SPICY, 원하는 맛 없음 (AI가 직전 원하는 맛을 이어 써도)")
        void avoidsSpicy_whenWantedThenSaidNoSpicy() {
            RecipeQuery query = refineAfter(tastes(List.of(Taste.SPICY), List.of()), "매운 거 말고",
                    List.of("SPICY"), List.of("SPICY"));

            assertThat(query.avoidTastes()).containsExactly(Taste.SPICY);
            assertThat(query.wantedTastes()).isEmpty();
        }

        @Test
        @DisplayName("\"짭짤한 거\" → \"담백한 거로\"면 반대인 짠맛을 지우고 원하는 맛 MILD만")
        void keepsOnlyMild_whenSaltyThenMild() {
            RecipeQuery query = refineAfter(tastes(List.of(Taste.SALTY), List.of()), "담백한 거로",
                    List.of("SALTY", "MILD"), List.of());

            assertThat(query.wantedTastes()).containsExactly(Taste.MILD);
        }

        @Test
        @DisplayName("\"매운 걸로\" → \"순한 걸로\"면 반대인 매운맛을 지우고 원하는 맛 MILD만")
        void keepsOnlyMild_whenSpicyThenMild() {
            RecipeQuery query = refineAfter(tastes(List.of(Taste.SPICY), List.of()), "순한 걸로", List.of("MILD"), List.of());

            assertThat(query.wantedTastes()).containsExactly(Taste.MILD);
        }

        @Test
        @DisplayName("\"매운 걸로\" → \"단 것도\"면 반대가 아니라 쌓아서 SPICY + SWEET (AI가 이번 맛만 내도)")
        void stacksNonOppositeTastes() {
            RecipeQuery query = refineAfter(tastes(List.of(Taste.SPICY), List.of()), "단 것도", List.of("SWEET"), List.of());

            assertThat(query.wantedTastes()).containsExactlyInAnyOrder(Taste.SPICY, Taste.SWEET);
        }

        @Test
        @DisplayName("\"땅콩 알레르기\" → \"매운 걸로\" → \"순한 걸로\"여도 땅콩은 계속 빠지고 원하는 맛은 MILD만")
        void keepsAllergyExclusion_acrossTasteChanges() {
            when(allergyExclusionFinder.find(eq(SESSION_ID), any())).thenReturn(
                    new AllergyExclusionFinder.Allergens(List.of("땅콩"), List.of(101L, 102L)));
            RecipeQuery first = tastes(List.of(), List.of()).withExcludedIngredients(List.of(101L, 102L));

            RecipeQuery spicy = refineAfter(first, "매운 걸로", List.of("SPICY"), List.of());
            RecipeQuery mild = refineAfter(spicy, "순한 걸로", List.of("MILD"), List.of());

            assertThat(spicy.excludeIngredientIds()).containsExactlyInAnyOrder(101L, 102L);
            assertThat(mild.wantedTastes()).containsExactly(Taste.MILD);
            assertThat(mild.excludeIngredientIds()).containsExactlyInAnyOrder(101L, 102L);
        }

        private static RecipeQuery tastes(List<Taste> wanted, List<Taste> avoid) {
            return new RecipeQuery(MEMBER_ID, List.of(), wanted, List.of(), null, List.of(), false, List.of(), null, null)
                    .withAvoidTastes(avoid);
        }

        // 직전 추천 조건(previous) 뒤에 message를 다시 추천으로 보낸다. AI는 wanted·avoid 맛을 낸다고 본다
        private RecipeQuery refineAfter(RecipeQuery previous, String message, List<String> wanted, List<String> avoid) {
            ChatContext context = new ChatContext(List.of(), LastRecommendation.of(previous, List.of(10L)),
                    List.of(new CardName(10L, "두부조림")));
            givenExtracted(new IntentResult(ChatIntent.REFINE, wanted, null, null, null, null, null, null, null, null,
                    null, null, null, avoid, null));
            return facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message), context).recipeQuery();
        }
    }

    @Nested
    @DisplayName("알레르기 재료 빼기")
    class Allergy {

        private final AllergyExclusionFinder.Allergens peanut = new AllergyExclusionFinder.Allergens(List.of("땅콩"), List.of(101L, 102L));

        @Test
        @DisplayName("추천이면 이 상담에서 말한 알레르기 재료를 뺄 재료에 더하고 이름을 남긴다 (AI가 뺄 재료로 넣지 않았어도)")
        void addsAllergyIngredientsToExcluded_whenRecommend() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null));
            when(allergyExclusionFinder.find(SESSION_ID, "땅콩 알레르기 있어, 뭐 먹지")).thenReturn(peanut);

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("땅콩 알레르기 있어, 뭐 먹지"));

            assertThat(resolution.recipeQuery().excludeIngredientIds()).containsExactly(101L, 102L);
            assertThat(resolution.allergens()).containsExactly("땅콩");
        }

        @Test
        @DisplayName("추천 칩(mode=RECOMMEND)에도 알레르기 재료를 빼고, 고른 재료에 있으면 고른 재료에서도 뺀다")
        void excludesAllergyFromSelected_whenRecommendMode() {
            when(allergyExclusionFinder.find(eq(SESSION_ID), any())).thenReturn(
                    new AllergyExclusionFinder.Allergens(List.of("두부"), List.of(TOFU)));
            ChatMessageCreateRequest request = new ChatMessageCreateRequest(
                    "두부, 버섯으로 추천해줘", ChatMessageMode.RECOMMEND, List.of(TOFU, MUSHROOM), false);

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, request).recipeQuery();

            assertThat(query.excludeIngredientIds()).containsExactly(TOFU);
            assertThat(query.selectedIngredientIds()).containsExactly(MUSHROOM);
        }

        @Test
        @DisplayName("다시 추천에서도 알레르기 재료를 계속 빼고, 뺄 재료를 지우라는 말(오이 넣어도 돼) 뒤에도 뺀다")
        void keepsAllergyExclusion_whenRefineEvenIfExcludeRemoved() {
            RecipeQuery previous = new RecipeQuery(MEMBER_ID, List.of(), List.of(), List.of(), null, List.of(15L, 101L, 102L), false,
                    List.of(), null, null);
            ChatContext context = new ChatContext(List.of(), LastRecommendation.of(previous, List.of(10L)),
                    List.of(new CardName(10L, "두부조림")));
            when(allergyExclusionFinder.find(eq(SESSION_ID), any())).thenReturn(peanut);
            givenExtracted(new IntentResult(ChatIntent.REFINE, null, null, null, null, null, null, null, null, null,
                    null, null, List.of("EXCLUDE")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("오이 넣어도 돼"), context).recipeQuery();

            assertThat(query.excludeIngredientIds()).containsExactlyInAnyOrder(101L, 102L).doesNotContain(15L);
        }

        @Test
        @DisplayName("\"매운 거 못 먹어\"는 알레르기 재료가 아니지만, AI가 원하는 맛으로 넣어도 서버가 피할 맛 SPICY로 고친다")
        void avoidsSpicy_whenCannotEatSpicy() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), List.of(), 0, List.of(), List.of(), false,
                    null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 못 먹어"));

            assertThat(resolution.recipeQuery().avoidTastes()).containsExactly(Taste.SPICY);
            assertThat(resolution.recipeQuery().wantedTastes()).isEmpty();
            assertThat(resolution.allergens()).isEmpty();
        }

        @Test
        @DisplayName("추천이 아니면(일반 답) 알레르기를 찾지 않는다")
        void skipsAllergy_whenGeneral() {
            givenExtracted(new IntentResult(ChatIntent.GENERAL, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("땅콩 알레르기 있어"));

            assertThat(resolution.allergens()).isEmpty();
            verify(allergyExclusionFinder, never()).find(any(), any());
        }
    }

    @Nested
    @DisplayName("안전 장치 의도 (서버 안내로 답함)")
    class Declined {

        @Test
        @DisplayName("AI가 OFF_TOPIC이라고 하면 서버 안내로 답하는 의도로 정하고 추천 조건은 만들지 않는다")
        void declines_whenIntentIsOffTopic() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("파이썬 코드 짜줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.OFF_TOPIC);
            assertThat(resolution.source()).isEqualTo(Source.AI);
            assertThat(resolution.declines()).isTrue();
            assertThat(resolution.recommends()).isFalse();
            assertThat(resolution.recipeQuery()).isNull();
        }

        @Test
        @DisplayName("AI가 BLOCKED·SELF_HARM이라고 하면 서버 안내로 답하는 의도로 정한다")
        void declines_whenIntentIsBlockedOrSelfHarm() {
            for (ChatIntent intent : List.of(ChatIntent.BLOCKED, ChatIntent.SELF_HARM)) {
                givenExtracted(intentOnly(intent));

                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("말"));

                assertThat(resolution.intent()).as(intent.name()).isEqualTo(intent);
                assertThat(resolution.declines()).as(intent.name()).isTrue();
                assertThat(resolution.recipeQuery()).as(intent.name()).isNull();
            }
        }

        @Test
        @DisplayName("AI 의도 판단이 실패하면 거절하지 않고 지금처럼 일반 답으로 정한다")
        void fallsBackToGeneral_whenExtractorFails() {
            when(extractor.extract(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("timeout"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("숙제 풀어줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.declines()).isFalse();
        }

        @Test
        @DisplayName("AI가 OFF_TOPIC이어도 음식 말이 있고 달라는 말이 있으면 추천으로 되살린다 (주식으로 먹을 거 추천해줘)")
        void revivesAsRecommend_whenFoodWordAndRequestWord() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("주식으로 먹을 거 추천해줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.declines()).isFalse();
            assertThat(resolution.recipeQuery()).isNotNull();
        }

        @Test
        @DisplayName("OFF_TOPIC을 추천으로 되살리면 맛·분류 보정이 돈다 (매운 국 먹을 거 추천해줘 → 원하는 맛 SPICY, 분류 국)")
        void correctsTastesAndCategories_whenRevivedAsRecommend() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 국 먹을 거 추천해줘")).recipeQuery();

            assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
            assertThat(query.moodCategories()).contains("국");
        }

        @Test
        @DisplayName("AI가 OFF_TOPIC이어도 음식 말이 있고 달라는 말이 없으면 일반 답으로 되살린다 (장보기 목록 짜줘)")
        void revivesAsGeneral_whenFoodWordWithoutRequestWord() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("장보기 목록 짜줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.GENERAL);
            assertThat(resolution.declines()).isFalse();
        }

        @Test
        @DisplayName("음식 말이 없어도 재료 사전 이름이 있으면 OFF_TOPIC을 되살린다")
        void revives_whenIngredientName() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));
            when(ingredientMatcher.matchAll(anyList())).thenReturn(Map.of("두부", ingredient(TOFU, "ING_TOFU", "두부")));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 값이 왜 올랐어"));

            assertThat(resolution.declines()).isFalse();
        }

        @Test
        @DisplayName("음식 말·분류·재료·레시피 이름이 없으면 OFF_TOPIC 그대로다 (주식 추천해줘, 파이썬 코드 짜줘)")
        void keepsOffTopic_whenNoFoodMention() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));

            for (String message : List.of("주식 추천해줘", "파이썬 코드 짜줘")) {
                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message));

                assertThat(resolution.intent()).as(message).isEqualTo(ChatIntent.OFF_TOPIC);
                assertThat(resolution.declines()).as(message).isTrue();
            }
        }

        @Test
        @DisplayName("레시피 이름 찾기가 실패해도 오류 없이 OFF_TOPIC 그대로다")
        void keepsOffTopic_whenRecipeNameLookupFails() {
            givenExtracted(intentOnly(ChatIntent.OFF_TOPIC));
            when(recipeService.suggestNames(any(), anyInt())).thenThrow(new RuntimeException("db down"));

            assertThat(facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("주식 추천해줘")).intent())
                    .isEqualTo(ChatIntent.OFF_TOPIC);
        }

        @Test
        @DisplayName("BLOCKED·SELF_HARM은 음식 말이 있어도 되살리지 않는다 (마약김밥 먹고 죽고 싶어)")
        void keepsBlockedAndSelfHarm_whenFoodWord() {
            for (ChatIntent intent : List.of(ChatIntent.BLOCKED, ChatIntent.SELF_HARM)) {
                givenExtracted(intentOnly(intent));

                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                        ChatMessageCreateRequest.of("마약김밥 먹고 죽고 싶어 추천해줘"));

                assertThat(resolution.intent()).as(intent.name()).isEqualTo(intent);
                assertThat(resolution.declines()).as(intent.name()).isTrue();
            }
        }

        @Test
        @DisplayName("OFF_TOPIC이면 안 되는 말은 AI가 GENERAL·RECOMMEND라고 한 대로 두고 서버 안내로 답하지 않는다")
        void keepsGeneralOrRecommend_whenFoodRelatedMessages() {
            Map<String, ChatIntent> messages = Map.of(
                    "주식으로 먹을 거 추천해줘", ChatIntent.RECOMMEND,
                    "장보기 목록 짜줘", ChatIntent.GENERAL,
                    "냉장고 정리 방법", ChatIntent.GENERAL,
                    "안녕", ChatIntent.GENERAL,
                    "밥 먹었어?", ChatIntent.GENERAL);
            messages.forEach((message, intent) -> {
                givenExtracted(intentOnly(intent));

                ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of(message));

                assertThat(resolution.intent()).as(message).isEqualTo(intent);
                assertThat(resolution.declines()).as(message).isFalse();
            });
        }

        private static IntentResult intentOnly(ChatIntent intent) {
            return new IntentResult(intent, List.of(), List.of(), 0, List.of(), List.of(), false, null, null, null);
        }
    }

    @Nested
    @DisplayName("냉장고 보기 (FRIDGE)")
    class Fridge {

        @Test
        @DisplayName("AI가 냉장고 보기로 보면 범위·말한 재료로 냉장고 보기를 정하고 추천 조건은 만들지 않는다")
        void resolvesFridge_whenAiSaysFridge() {
            givenExtracted(fridge(List.of("두부"), "ALL"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 아직 있어?"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.FRIDGE);
            assertThat(resolution.recommends()).isFalse();
            assertThat(resolution.recipeQuery()).isNull();
            assertThat(resolution.lookup().names()).containsExactly("두부");
        }

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
        @DisplayName("AI가 재료를 비웠으면 서버가 말에서 재료 사전 이름을 찾는다")
        void fillsIngredientNames_whenAiLeftEmpty() {
            givenExtracted(intentOnly(ChatIntent.GENERAL));
            when(ingredientMatcher.matchAll(anyList())).thenReturn(Map.of("두부", ingredient(TOFU, "ING_TOFU", "두부")));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 아직 있어?"));

            assertThat(resolution.lookup().names()).containsExactly("두부");
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
        @DisplayName("AI가 정한 범위가 있으면 그 범위를 쓴다")
        void keepsAiScope() {
            givenExtracted(fridge(List.of(), "IMMINENT"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("냉장고 좀 봐줘"));

            assertThat(resolution.lookup().scope()).isEqualTo(FridgeScope.IMMINENT);
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
        @DisplayName("AI가 사기로 봐도 요리 말이 있으면 추천으로 둔다 (두부 사서 만들 요리 추천해줘)")
        void keepsRecommend_whenCookWordWithShopIntent() {
            givenExtracted(new IntentResult(ChatIntent.SHOP, List.of(), List.of(), 0, List.of("두부"), List.of(), false, null, null, null));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부 사서 만들 요리 추천해줘"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
        }
    }

    private void givenExtracted(IntentResult result) {
        when(extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any(), any())).thenReturn(result);
    }

    private static Ingredient ingredient(Long id, String code, String name) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("g")
                .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
