package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.MealkitWithPrice;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.ProductWithPrice;
import org.example.takecareofmyfridgebe.domain.chat.recommend.MealkitPicker.FridgeMealkits;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.candidate;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.mealkit;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.missing;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.product;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.reason;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.soldOutMealkit;
import static org.example.takecareofmyfridgebe.domain.chat.facade.RecommendationCardFixture.soldOutProduct;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCardRebuilderTest {

    private static final Long MEMBER_ID = 1L;

    private RecipeCandidateFacade recipeCandidateFacade;
    private RecipeService recipeService;
    private ProductService productService;
    private SaleEventReader saleEventReader;
    private ChatCardRebuilder facade;

    @BeforeEach
    void setUp() {
        recipeCandidateFacade = mock(RecipeCandidateFacade.class);
        recipeService = mock(RecipeService.class);
        productService = mock(ProductService.class);
        saleEventReader = mock(SaleEventReader.class);
        facade = new ChatCardRebuilder(recipeCandidateFacade, recipeService, productService, saleEventReader);
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
    @DisplayName("저장된 카드를 다시 만들 때도 장볼 재료 상품을 지금 품절 기준으로 붙이고, 메시지가 여럿이어도 한 번씩 조회한다")
    void attachesCurrentProducts_whenRebuildingCards() {
        List<SavedRecommendation> saved = List.of(
                new SavedRecommendation(2L, 10L, 0, "첫째 설명"),
                new SavedRecommendation(4L, 20L, 0, "둘째 설명"));
        when(recipeCandidateFacade.describeRecipes(1L, List.of(10L, 20L))).thenReturn(Map.of(
                10L, candidate(10L, "두부조림", "조림", 20, missing(new MissingIngredient(21L, "두부"))),
                20L, candidate(20L, "대파전", "전", 20, missing(new MissingIngredient(3L, "대파")))));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
        givenProducts(product(22L, 21L, "부침용 두부 1모", 2000, null), soldOutProduct(16L, 3L, "대파 1단", 3000));

        Map<Long, List<RecommendedRecipe>> cards = facade.rebuildCards(1L, saved);

        assertThat(cards.get(2L).get(0).missingIngredients().get(0).product().soldOut()).isFalse();
        assertThat(cards.get(4L).get(0).missingIngredients().get(0).product().soldOut()).isTrue();
        verify(productService, times(1)).getPublicIngredientProducts(any());
        verify(saleEventReader, times(1)).findSellingPrices(any());
    }

    @Test
    @DisplayName("저장된 밀키트 카드를 메시지별 순서·설명 그대로 지금 가격으로 다시 만들고, 판매 중지된 밀키트는 빼며 조회는 한 번씩 한다")
    void rebuildsMealkitCards_withCurrentPrice() {
        List<SavedRecommendation> saved = List.of(
                new SavedRecommendation(2L, 10L, null, 0, "레시피 설명"),
                new SavedRecommendation(2L, null, 41L, 1, "요리하기 번거로우면 황태해장국 밀키트로 간편하게 드셔 보세요."),
                new SavedRecommendation(4L, null, 43L, 1, null),
                new SavedRecommendation(4L, null, 40L, 0, null),
                new SavedRecommendation(4L, null, 99L, 2, null));
        MealkitWithPrice hwangtae = mealkit(41L, "황태해장국", 9000, 543L);
        MealkitWithPrice doenjang = mealkit(40L, "된장국", 7000, 544L);
        MealkitWithPrice soldOut = soldOutMealkit(43L, "부추 콩가루 찜", 8000);
        when(productService.getPublicMealkits(List.of(41L, 43L, 40L, 99L)))
                .thenReturn(List.of(hwangtae.mealkit(), doenjang.mealkit(), soldOut.mealkit()));
        when(saleEventReader.findSellingPrices(any())).thenReturn(Map.of(
                41L, hwangtae.price(), 40L, doenjang.price(), 43L, soldOut.price()));
        when(recipeService.findSummaries(any())).thenReturn(Map.of(
                543L, new RecipeSummary(543L, "황태해장국", null, null),
                544L, new RecipeSummary(544L, "된장국", null, null)));
        when(recipeCandidateFacade.findFridgeMealkits(MEMBER_ID)).thenReturn(new FridgeMealkits(Map.of(40L, 1L), Map.of()));

        Map<Long, List<RecommendedMealkit>> cards = facade.rebuildMealkitCards(MEMBER_ID, saved);

        assertThat(cards.get(2L)).extracting(m -> m.product().productId(), RecommendedMealkit::description)
                .containsExactly(tuple(41L, "요리하기 번거로우면 황태해장국 밀키트로 간편하게 드셔 보세요."));
        assertThat(cards.get(4L)).extracting(m -> m.product().productId()).containsExactly(40L, 43L);
        assertThat(cards.get(4L).get(0).recipeName()).isEqualTo("된장국");
        assertThat(cards.get(4L).get(1).product().soldOut()).isTrue();
        // 냉장고에 있는지는 지금 냉장고 기준 (된장국 밀키트가 냉장고에 있음)
        assertThat(cards.get(4L)).extracting(RecommendedMealkit::inFridge, RecommendedMealkit::fridgeDDay)
                .containsExactly(tuple(true, 1L), tuple(false, null));
        verify(productService, times(1)).getPublicMealkits(any());
    }

    @Test
    @DisplayName("레시피 카드를 다시 만들 때 밀키트 카드는 건너뛰고, 밀키트만 있는 답이면 레시피를 조회하지 않는다")
    void skipsMealkitRows_whenRebuildingRecipeCards() {
        Map<Long, List<RecommendedRecipe>> cards = facade.rebuildCards(1L, List.of(new SavedRecommendation(4L, null, 40L, 0, null)));

        assertThat(cards).isEmpty();
        verify(recipeCandidateFacade, never()).describeRecipes(any(), any());
    }

    // 상품 조회는 받은 연결을 돌려주고, 판매가는 상품마다 계산해 둔 값을 돌려준다
    private void givenProducts(ProductWithPrice... products) {
        RecommendationCardFixture.givenProducts(productService, saleEventReader, products);
    }
}
