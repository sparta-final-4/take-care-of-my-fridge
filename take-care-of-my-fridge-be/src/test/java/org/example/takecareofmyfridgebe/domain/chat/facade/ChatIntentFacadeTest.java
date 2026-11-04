package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.CardName;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatIntentFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TOFU = 21L;
    private static final Long MUSHROOM = 161L;
    private static final Long CUCUMBER = 15L;

    private ChatIntentExtractor extractor;
    private RecipeService recipeService;
    private IngredientMatcher ingredientMatcher;
    private ChatMessageService chatMessageService;
    private ChatIntentFacade facade;

    @BeforeEach
    void setUp() {
        extractor = mock(ChatIntentExtractor.class);
        recipeService = mock(RecipeService.class);
        ingredientMatcher = mock(IngredientMatcher.class);
        chatMessageService = mock(ChatMessageService.class);
        facade = new ChatIntentFacade(extractor, recipeService, ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4), chatMessageService);
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

        private static IntentResult detail(Integer index, String name) {
            return new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null, index, name);
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
        @DisplayName("\"매운 거 말고\"처럼 removed에 맛이 있으면 직전 맛 조건을 지우고 나머지는 직전 그대로 둔다")
        void removesTaste_whenNotSpicy() {
            givenExtracted(refine(null, null, List.of("TASTE")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("매운 거 말고"), context)
                    .recipeQuery();

            assertThat(query.wantedTastes()).isEmpty();
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

        private static IntentResult refine(List<String> tastes, List<String> categories) {
            return refine(tastes, categories, null);
        }

        private static IntentResult refine(List<String> tastes, List<String> categories, List<String> removed) {
            return new IntentResult(ChatIntent.REFINE, tastes, categories, null, null, null, null, null, null, null,
                    null, null, removed);
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
