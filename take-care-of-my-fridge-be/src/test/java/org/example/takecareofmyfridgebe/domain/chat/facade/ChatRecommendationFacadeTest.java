package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRecommendationFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private RecipeCandidateFacade recipeCandidateFacade;
    private RecipeService recipeService;
    private ChatRecommendationFacade facade;

    @BeforeEach
    void setUp() {
        recipeCandidateFacade = mock(RecipeCandidateFacade.class);
        recipeService = mock(RecipeService.class);
        IngredientService ingredientService = mock(IngredientService.class);
        facade = new ChatRecommendationFacade(recipeCandidateFacade, recipeService, ingredientService);
        when(ingredientService.getIngredientsByIds(any())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("후보 위 3개로 카드를 만들고 이미지는 레시피 요약에서, 맛은 원한 맛을 한글로 채운다")
    void buildsCardsFromTopThree() {
        Reason wanted = new Reason(List.of("양파", "두부"), List.of(new ImminentIngredient("양파", 0)),
                List.of(Taste.SALTY), List.of(Taste.SPICY), List.of(), false, true,
                List.of(new MissingIngredient(5L, "돼지고기")), List.of("돼지고기"), List.of(), null);
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "두부찌개", "찌개", 25, wanted),
                candidate(20L, "두부조림", "조림", null, reason(List.of(Taste.SALTY))),
                candidate(30L, "두부전", "전", 15, reason(List.of())),
                candidate(40L, "두부국", "국", 10, reason(List.of()))), false));
        when(recipeService.findSummaries(List.of(10L, 20L, 30L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "두부찌개", "https://img/10.jpg", null)));

        RecommendationReply reply = facade.recommend("두부로 매운 거", RecipeQuery.of(MEMBER_ID, List.of()));

        ChatRecommendationsEventResponse event = reply.event();
        assertThat(event.empty()).isFalse();
        assertThat(event.relaxed()).isEmpty();
        assertThat(event.recipes()).extracting(RecommendedRecipe::recipeId).containsExactly(10L, 20L, 30L);
        RecommendedRecipe first = event.recipes().get(0);
        assertThat(first.imageUrl()).isEqualTo("https://img/10.jpg");
        assertThat(first.category()).isEqualTo("찌개");
        assertThat(first.cookTimeMin()).isEqualTo(25);
        assertThat(first.usedIngredients()).containsExactly("양파", "두부");
        assertThat(first.imminentIngredients()).containsExactly(new ChatRecommendationsEventResponse.ImminentIngredient("양파", 0));
        assertThat(first.matchedTastes()).containsExactly("매운맛");
        assertThat(first.missingIngredients()).containsExactly(new ChatRecommendationsEventResponse.MissingIngredient(5L, "돼지고기"));
        assertThat(first.missingSelectedIngredients()).containsExactly("돼지고기");
        assertThat(first.lacksManyIngredients()).isTrue();
        // 원한 맛(매운맛)이 맞아 임박 재료보다 먼저 쓴다
        assertThat(first.reason()).isEqualTo("매콤한 요리예요");
        assertThat(event.recipes().get(1).imageUrl()).isNull();
        assertThat(event.recipes().get(1).cookTimeMin()).isNull();
        assertThat(event.recipes().get(1).matchedTastes()).containsExactly("짠맛");
        assertThat(reply.candidateInfo()).contains("후보: 두부찌개", "후보: 두부조림", "후보: 두부전").doesNotContain("두부국");
    }

    @Test
    @DisplayName("후보가 없으면 빈 카드와 empty=true, 프롬프트에는 후보 없음을 넣는다")
    void returnsEmpty_whenNoCandidates() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(), true));
        when(recipeService.findSummaries(List.of())).thenReturn(Map.of());

        RecommendationReply reply = facade.recommend("뭐 먹지?", RecipeQuery.of(MEMBER_ID, List.of()));

        assertThat(reply.event().recipes()).isEmpty();
        assertThat(reply.event().empty()).isTrue();
        assertThat(reply.candidateInfo()).endsWith("[추천 후보]\n후보 없음");
    }

    @Test
    @DisplayName("카드 이벤트 JSON은 recipes·relaxed·empty와 임박 재료 dDay를 담는다")
    void serializesEventJson() {
        ChatRecommendationsEventResponse event = ChatRecommendationsEventResponse.of(List.of(new RecommendedRecipe(
                10L, "두부찌개", null, "찌개", null, List.of("양파"),
                List.of(new ChatRecommendationsEventResponse.ImminentIngredient("양파", 0)), List.of("매운맛"),
                List.of(), List.of(), false, "이유", null)));

        String json = new ObjectMapper().writeValueAsString(event);

        assertThat(json).contains("\"recipes\":[", "\"relaxed\":[]", "\"empty\":false",
                "\"imminentIngredients\":[{\"name\":\"양파\",\"dDay\":0}]", "\"cookTimeMin\":null", "\"imageUrl\":null", "\"reason\":\"이유\"");
    }

    @Test
    @DisplayName("저장된 카드를 순서·설명 그대로 다시 만들고, 지워진 레시피는 빼며, 레시피 계산은 한 번에 한다")
    void rebuildsCardsInOrder_skippingDeletedRecipe() {
        List<SavedRecommendation> saved = List.of(
                new SavedRecommendation(2L, 20L, 1, "둘째 설명"),
                new SavedRecommendation(2L, 10L, 0, "첫째 설명"),
                new SavedRecommendation(2L, 99L, 2, "지워진 레시피"),
                new SavedRecommendation(4L, 30L, 0, null));
        when(recipeCandidateFacade.describeRecipes(1L, List.of(20L, 10L, 99L, 30L))).thenReturn(Map.of(
                10L, candidate(10L, "두부조림", "조림", 20, reason(List.of(Taste.SALTY))),
                20L, candidate(20L, "두부국", "국", null, reason(List.of())),
                30L, candidate(30L, "장어조림", "조림", 30, reason(List.of()))));
        when(recipeService.findSummaries(List.of(20L, 10L, 99L, 30L)))
                .thenReturn(Map.of(10L, new RecipeSummary(10L, "두부조림", "https://img/10.jpg", null)));

        Map<Long, List<RecommendedRecipe>> cards = facade.rebuildCards(1L, saved);

        assertThat(cards.get(2L)).extracting(RecommendedRecipe::recipeId).containsExactly(10L, 20L);
        assertThat(cards.get(2L)).extracting(RecommendedRecipe::description).containsExactly("첫째 설명", "둘째 설명");
        assertThat(cards.get(2L).get(0).imageUrl()).isEqualTo("https://img/10.jpg");
        assertThat(cards.get(2L).get(0).missingSelectedIngredients()).isEmpty();
        assertThat(cards.get(4L)).extracting(RecommendedRecipe::recipeId).containsExactly(30L);
        verify(recipeCandidateFacade, times(1)).describeRecipes(any(), any());
    }

    @Test
    @DisplayName("다시 추천이면 직전 카드 3개를 후보에서 빼고 그다음 후보로 카드를 만든다")
    void dropsPreviousCards_whenRefine() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "김치찌개", "찌개", 20, reason(List.of())),
                candidate(20L, "순두부찌개", "찌개", 20, reason(List.of())),
                candidate(30L, "두부국", "국", 20, reason(List.of())),
                candidate(40L, "부대찌개", "찌개", 20, reason(List.of())),
                candidate(50L, "된장국", "국", 20, reason(List.of())),
                candidate(60L, "제육볶음", "볶음", 20, reason(List.of()))), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        RecommendationReply reply = facade.recommend("다른 거 없어?", RecipeQuery.of(MEMBER_ID, List.of()), List.of(10L, 20L, 30L));

        assertThat(reply.event().recipes()).extracting(RecommendedRecipe::recipeId).containsExactly(40L, 50L, 60L);
    }

    @Test
    @DisplayName("직전 카드를 빼서 후보가 3개보다 적어지면 그때만 직전 카드를 정렬 순서대로 뒤에 다시 넣는다")
    void putsPreviousCardsBack_whenTooFew() {
        List<RecipeCandidate> candidates = List.of(
                candidate(10L, "김치찌개", "찌개", 20, reason(List.of())),
                candidate(40L, "부대찌개", "찌개", 20, reason(List.of())),
                candidate(20L, "순두부찌개", "찌개", 20, reason(List.of())),
                candidate(30L, "두부국", "국", 20, reason(List.of())));

        assertThat(ChatRecommendationFacade.withoutPrevious(candidates, List.of(10L, 20L, 30L)))
                .extracting(RecipeCandidate::recipeId).containsExactly(40L, 10L, 20L, 30L);
        assertThat(ChatRecommendationFacade.withoutPrevious(candidates, List.of()))
                .extracting(RecipeCandidate::recipeId).containsExactly(10L, 40L, 20L, 30L);
    }

    private static RecipeCandidate candidate(Long id, String name, String category, Integer cookTimeMin, Reason reason) {
        return new RecipeCandidate(id, name, category, cookTimeMin, new Ranking(0, 0, 0, 0, 1, 1, 0), reason);
    }

    private static Reason reason(List<Taste> tastes) {
        return new Reason(List.of(), List.of(), tastes, List.of(), List.of(), false, false, List.of(), List.of(), List.of(), null);
    }
}
