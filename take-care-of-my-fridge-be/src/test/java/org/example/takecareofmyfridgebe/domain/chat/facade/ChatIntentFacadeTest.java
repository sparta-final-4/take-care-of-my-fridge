package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.Source;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageMode;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatIntentExtractor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
    private ChatIntentFacade facade;

    @BeforeEach
    void setUp() {
        extractor = mock(ChatIntentExtractor.class);
        recipeService = mock(RecipeService.class);
        ingredientMatcher = mock(IngredientMatcher.class);
        facade = new ChatIntentFacade(extractor, recipeService, ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4));
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
            verify(extractor, never()).extract(any(), any(), any(), any());
            verify(ingredientMatcher, never()).matchAll(anyList());
        }
    }

    @Nested
    @DisplayName("AI 판단 (mode=AUTO)")
    class AutoMode {

        @Test
        @DisplayName("AI가 RECOMMEND라고 하면 정리한 맛·분류·시간으로 검색 조건을 만든다")
        void buildsRecipeQuery_whenIntentIsRecommend() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND,
                    List.of("SPICY", "BITTER"), List.of("국", "디저트"), 30, null, null, null));

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

            verify(extractor).extract(MEMBER_ID, SESSION_ID, "뭐 먹지?", List.of("국", "샐러드", "찌개"));
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
            when(extractor.extract(any(), any(), any(), any())).thenThrow(new RuntimeException("timeout"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("이걸로", ChatMessageMode.AUTO, List.of(TOFU), true));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.source()).isEqualTo(Source.FALLBACK);
            assertThat(resolution.recipeQuery()).isEqualTo(RecipeQuery.of(MEMBER_ID, List.of(TOFU), true));
        }

        @Test
        @DisplayName("AI 호출이 실패하고 선택 재료가 없으면 일반 답으로 정한다")
        void returnsGeneral_whenExtractorFailsWithoutSelected() {
            when(extractor.extract(any(), any(), any(), any())).thenThrow(new IllegalStateException("broken json"));

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
            verify(extractor, never()).extract(any(), any(), any(), any());
        }

        @Test
        @DisplayName("선택 재료만 쓰기는 요청값이 true면 AI가 false라고 해도 true다")
        void keepsUseSelectedOnly_whenRequestIsTrue() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, false));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID,
                    new ChatMessageCreateRequest("추천해줘", ChatMessageMode.AUTO, List.of(TOFU), true));

            assertThat(resolution.recipeQuery().useSelectedOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("재료 이름")
    class IngredientNames {

        @Test
        @DisplayName("말한 재료 이름을 재료 ID로 바꾸고, 못 찾은 이름(ING_CUSTOM)은 버린다")
        void mapsNamesToIds_andDropsCustom() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null,
                    List.of("두부", "용과즙"), List.of("오이"), true));
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
                    List.of("두부"), null, false));
            when(ingredientMatcher.matchAll(List.of("두부"))).thenReturn(Map.of("두부", ingredient(TOFU, "ING_TOFU", "두부")));

            RecipeQuery query = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부도 넣어서 매운 거"))
                    .recipeQuery();

            assertThat(query.selectedIngredientIds()).containsExactly(TOFU);
            assertThat(query.useSelectedOnly()).isFalse();
        }

        @Test
        @DisplayName("칩 없이 말로만 재료를 정하면 그 재료가 선택 재료가 된다")
        void usesSpokenIngredientsAsSelected_whenNoChip() {
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, List.of("버섯", "두부"), null, true));
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
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, List.of("두부", "버섯"), null, null));
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
            givenExtracted(new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, List.of("오이"), null));
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
                    List.of("두부"), null, null));
            when(ingredientMatcher.matchAll(eq(List.of("두부")))).thenThrow(new RuntimeException("es down"));

            ChatIntentResolution resolution = facade.resolve(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("두부로 매운 거"));

            assertThat(resolution.intent()).isEqualTo(ChatIntent.RECOMMEND);
            assertThat(resolution.recipeQuery().selectedIngredientIds()).isEmpty();
            assertThat(resolution.recipeQuery().wantedTastes()).containsExactly(Taste.SPICY);
        }
    }

    private void givenExtracted(IntentResult result) {
        when(extractor.extract(eq(MEMBER_ID), eq(SESSION_ID), any(), any())).thenReturn(result);
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
