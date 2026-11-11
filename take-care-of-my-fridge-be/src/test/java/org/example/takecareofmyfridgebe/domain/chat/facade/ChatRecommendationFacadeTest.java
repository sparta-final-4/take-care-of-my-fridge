package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.recommend.MealkitPicker.FridgeMealkits;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeDetailReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.recommend.MealkitPicker;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse.StepItem;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRecommendationFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private RecipeCandidateFacade recipeCandidateFacade;
    private RecipeService recipeService;
    private IngredientService ingredientService;
    private ProductService productService;
    private SaleEventReader saleEventReader;
    private ChatMessageService chatMessageService;
    private ChatRecommendationFacade facade;

    @BeforeEach
    void setUp() {
        recipeCandidateFacade = mock(RecipeCandidateFacade.class);
        recipeService = mock(RecipeService.class);
        ingredientService = mock(IngredientService.class);
        productService = mock(ProductService.class);
        saleEventReader = mock(SaleEventReader.class);
        chatMessageService = mock(ChatMessageService.class);
        facade = new ChatRecommendationFacade(recipeCandidateFacade, recipeService, ingredientService, productService,
                saleEventReader, chatMessageService, ChatIntentFacadeTest.WORDS, AllergyWordCheckerTest.WORDS);
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
    @DisplayName("피할 맛인데 콕 집어 남긴 요리 카드는 이유 뒤에 \"매운 요리예요\"를 붙이고, AI 후보 줄도 같은 이유를 쓰며 나머지 카드는 그대로다")
    void notesAvoidedTaste_onKeptNamedDish() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "김치찌개", "찌개", 25, reason(List.of())),
                candidate(20L, "된장찌개", "찌개", 20, reason(List.of())),
                candidate(40L, "청국장찌개", "찌개", 30, reason(List.of()))), false, false, Map.of(10L, List.of(Taste.SPICY))));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        RecommendationReply reply = facade.recommend("매운 거 말고 김치찌개 줘", new RecipeQuery(MEMBER_ID, List.of(), null, null,
                null, null, false, List.of("김치찌개"), "김치찌개").withAvoidTastes(List.of(Taste.SPICY)));

        List<RecommendedRecipe> recipes = reply.event().recipes();
        assertThat(recipes.get(0).reason()).endsWith(", 매운 요리예요");
        assertThat(recipes.get(1).reason()).doesNotContain("매운 요리예요");
        assertThat(recipes.get(2).reason()).doesNotContain("매운 요리예요");
        assertThat(reply.candidateInfo()).contains(recipes.get(0).reason());
    }

    @Test
    @DisplayName("분류와 원하는 맛을 말했는데 둘 다 맞은 후보가 2개뿐이면 다른 분류로 채우지 않고 카드 2장과 후보 수 2개를 넘긴다")
    void returnsTwoCards_whenOnlyTwoMatchAllConditions() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                requested(10L, "꽃게해물탕", "국", 2),
                requested(20L, "토마토 해물누룽지탕", "국", 2),
                requested(30L, "오이무침", "무침", 0)), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        RecommendationReply reply = facade.recommend("맵고 따뜻한 국물 레시피 추천해줘", new RecipeQuery(MEMBER_ID, List.of(),
                List.of(Taste.SPICY), List.of("국"), null, null, false, List.of(), null, "따뜻한 국물"));

        assertThat(reply.event().recipes()).extracting(RecommendedRecipe::recipeId).containsExactly(10L, 20L);
        assertThat(reply.candidateInfo()).contains("후보 수: 2개").doesNotContain("오이무침");
    }

    @Test
    @DisplayName("조건에 맞는 후보도 채울 후보도 없으면 0장이 아니라 같은 후보에서 조건 없이 3장을 고르고 맞는 레시피가 없었다고 안내한다")
    void picksUnconditioned_whenNothingMatches() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                requested(10L, "오이무침", "무침", 0),
                requested(20L, "두부조림", "조림", 0),
                requested(30L, "버섯볶음", "볶음", 0),
                requested(40L, "가지구이", "구이", 0)), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        RecommendationReply reply = facade.recommend("시원한 국물 요리", new RecipeQuery(MEMBER_ID, List.of(),
                null, List.of("국"), null, null, false, List.of(), null, "시원한 국물"));

        assertThat(reply.event().recipes()).extracting(RecommendedRecipe::recipeId).containsExactly(10L, 20L, 30L);
        assertThat(reply.notices())
                .containsExactly("시원한 국물에 딱 맞는 레시피가 없어서, 냉장고 재료로 만들 수 있는 요리를 골랐어요");
    }

    @Test
    @DisplayName("따뜻한 국물을 원하면 분류가 맞은 오이냉국은 빼고, 이름에 냉이가 든 찌개는 남긴다")
    void removesColdSoup_whenWarmRequested() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                requested(10L, "꽃게해물탕", "국", 2),
                requested(20L, "토마토 해물누룽지탕", "국", 2),
                requested(30L, "오이냉국", "국", 1),
                requested(40L, "냉이된장국", "국", 1)), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        RecommendationReply reply = facade.recommend("맵고 따뜻한 국물 레시피 추천해줘", new RecipeQuery(MEMBER_ID, List.of(),
                List.of(Taste.SPICY), List.of("국"), null, null, false, List.of(), null, "따뜻한 국물"));

        assertThat(reply.event().recipes()).extracting(RecommendedRecipe::recipeId).containsExactly(10L, 20L, 40L);
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
    @DisplayName("고른 카드들의 장볼 재료를 모아 상품 조회·판매가 계산을 한 번씩 하고, 재료마다 상품을 붙이며 상품이 없는 재료는 비운다")
    void attachesProductsToMissingIngredients_withOneLookup() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "두부조림", "조림", 20, missing(new MissingIngredient(21L, "두부"), new MissingIngredient(3L, "대파"))),
                candidate(20L, "배추국", "국", 20, missing(new MissingIngredient(5L, "배추"), new MissingIngredient(3L, "대파"))),
                candidate(30L, "계란찜", "찜", 20, missing())), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
        givenProducts(product(16L, 3L, "대파 1단", 3000, null), product(22L, 21L, "부침용 두부 1모", 2000, 1500));

        RecommendationReply reply = facade.recommend("뭐 먹지?", RecipeQuery.of(MEMBER_ID, List.of()));

        List<RecommendedRecipe> cards = reply.event().recipes();
        ShoppingProduct tofu = cards.get(0).missingIngredients().get(0).product();
        assertThat(tofu.productId()).isEqualTo(22L);
        assertThat(tofu.price()).isEqualTo(2000);
        assertThat(tofu.salePrice()).isEqualTo(1500);
        assertThat(tofu.saleType()).isEqualTo(SaleEventType.SPECIAL);
        assertThat(cards.get(0).missingIngredients().get(1).product().productId()).isEqualTo(16L);
        assertThat(cards.get(1).missingIngredients().get(0).product()).isNull();
        assertThat(cards.get(1).missingIngredients().get(1).product().productId()).isEqualTo(16L);
        verify(productService, times(1)).getPublicIngredientProducts(Set.of(21L, 3L, 5L));
        verify(saleEventReader, times(1)).findSellingPrices(any());
    }

    @Test
    @DisplayName("고른 카드에 장볼 재료가 없으면 상품도 판매가도 조회하지 않는다")
    void skipsProductLookup_whenNoMissingIngredients() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "계란찜", "찜", 20, missing())), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        facade.recommend("뭐 먹지?", RecipeQuery.of(MEMBER_ID, List.of()));

        verify(productService, never()).getPublicIngredientProducts(any());
        verify(saleEventReader, never()).findSellingPrices(any());
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
    @DisplayName("레시피 자세히 카드의 장볼 재료에도 상품을 붙인다")
    void attachesProducts_whenRecipeDetail() {
        RecipeDetailResponse recipe = RecipeDetailResponse.builder()
                .recipeId(20L).name("두부조림").category("조림").cookTimeMin(20)
                .ingredients(List.of()).steps(List.of(new StepItem(1, "두부를 굽는다.", null, null))).build();
        when(recipeService.getRecipe(20L)).thenReturn(recipe);
        when(recipeCandidateFacade.describeRecipes(MEMBER_ID, List.of(20L))).thenReturn(Map.of(
                20L, candidate(20L, "두부조림", "조림", 20, missing(new MissingIngredient(21L, "두부")))));
        givenProducts(product(22L, 21L, "부침용 두부 1모", 2000, null));

        RecipeDetailReply reply = facade.describe(MEMBER_ID, 20L).orElseThrow();

        assertThat(reply.event().recipes().get(0).missingIngredients().get(0).product().productId()).isEqualTo(22L);
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

    @Test
    @DisplayName("레시피 자세히는 지금 냉장고 기준 카드 하나(이유: 물어보신 레시피예요)와 레시피 정보를 만든다")
    void describesRecipeWithOneCard() {
        RecipeDetailResponse recipe = RecipeDetailResponse.builder()
                .recipeId(20L).name("깐풍기").category("튀김").cookTimeMin(40).imageUrl("https://img/20.jpg")
                .ingredients(List.of()).steps(List.of(new StepItem(1, "닭을 튀긴다.", null, null))).build();
        when(recipeService.getRecipe(20L)).thenReturn(recipe);
        when(recipeCandidateFacade.describeRecipes(MEMBER_ID, List.of(20L))).thenReturn(Map.of(
                20L, candidate(20L, "깐풍기", "튀김", 40, reason(List.of()))));

        RecipeDetailReply reply = facade.describe(MEMBER_ID, 20L).orElseThrow();

        assertThat(reply.event().recipes()).hasSize(1);
        RecommendedRecipe card = reply.event().recipes().get(0);
        assertThat(card.recipeId()).isEqualTo(20L);
        assertThat(card.imageUrl()).isEqualTo("https://img/20.jpg");
        assertThat(card.reason()).isEqualTo("물어보신 레시피예요");
        // 설명으로도 넣어 저장하므로 답 받을 때·다시 불러올 때 모두 이유 줄 대신 이 줄만 보인다
        assertThat(card.description()).isEqualTo("물어보신 레시피예요");
        assertThat(reply.recipeInfo()).startsWith("[레시피 정보]\n이름: 깐풍기").contains("1. 닭을 튀긴다.", "[냉장고 기준]");
    }

    @Test
    @DisplayName("레시피가 지워졌으면 빈 값을 돌려준다")
    void returnsEmpty_whenRecipeIsDeleted() {
        when(recipeService.getRecipe(20L)).thenThrow(new BusinessException(ErrorCode.RECIPE_NOT_FOUND));

        assertThat(facade.describe(MEMBER_ID, 20L)).isEmpty();
    }

    @Test
    @DisplayName("요리 이름(탕짜면)이 레시피 이름에 없으면 못 찾았다는 안내와 비슷한 요리 이유를 쓴다")
    void writesNotFoundNotice_whenDishHasNoRecipe() {
        // 요리 이름 키워드를 찾지 못했다는 판단(keywordsNotFound)은 후보 찾기(RecipeCandidateFacade)가 한다
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                keywordCandidate(10L, "탕수육", "튀김", "탕수육"), keywordCandidate(20L, "황태볶음면", "면류", "볶음면")), true, true));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("탕짜면", "탕수육", "볶음면"), "탕짜면");

        RecommendationReply reply = facade.recommend("탕짜면 먹고 싶어", query);

        assertThat(reply.notices()).containsExactly("탕짜면 레시피는 없어서, 비슷한 탕수육과 볶음면을 골랐어요");
        assertThat(reply.event().recipes().get(0).reason()).isEqualTo("탕짜면과 비슷한 탕수육이에요");
    }

    @Test
    @DisplayName("AI가 요리 이름을 비웠으면 말에 있는 키워드(탕짜면)가 레시피 이름에 없어도 서버가 요리 이름을 채우지 않아 안내가 없다")
    void keepsDishTextEmpty_whenAiLeftItEmpty() {
        givenKeywordCandidates(keywordCandidate(10L, "탕수육", "튀김", "탕수육"),
                keywordCandidate(20L, "황태볶음면", "면류", "볶음면"));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("탕짜면", "탕수육", "볶음면"), null);

        RecommendationReply reply = facade.recommend("탕짜면 먹고 싶어", query);

        assertThat(reply.notices()).isEmpty();
        assertThat(reply.event().recipes().get(0).reason()).isEqualTo("탕수육 요리예요");
        verify(recipeCandidateFacade).findCandidates(argThat(sent -> sent.keywordText() == null));
    }

    @Test
    @DisplayName("특징만 말해 요리 이름이 없으면(시원한 국물) 키워드에 그 말이 있어도 없다는 안내 없이 원한 특징으로 넘긴다")
    void skipsNotFoundNotice_whenOnlyFeatureSpoken() {
        givenKeywordCandidates(keywordCandidate(10L, "오이냉국", "국", "냉국"),
                keywordCandidate(20L, "미역냉국", "국", "냉국"), keywordCandidate(30L, "다시마냉국", "국", "냉국"));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("시원한 국물", "냉국"), null, "시원한 국물");

        RecommendationReply reply = facade.recommend("시원한 국물 요리", query);

        assertThat(reply.notices()).isEmpty();
        assertThat(reply.candidateInfo()).contains("사용자가 원한 특징: 시원한 국물")
                .doesNotContain("없어서", "찾지 못해서", "[이미 안내한 말]");
        assertThat(reply.event().recipes().get(0).reason()).doesNotContain("시원한 국물과 비슷한");
    }

    @Test
    @DisplayName("요리 이름(닭볶음탕)을 말했는데 키워드로 맞은 레시피가 없으면 지금처럼 못 찾았다는 안내를 붙인다")
    void keepsNotFoundNotice_whenDishSpoken() {
        // 요리 이름은 못 찾았지만 다른 요청(분류 등)에는 맞은 후보
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                requested(10L, "닭조림", "조림", 1)), true, true));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false,
                List.of("닭볶음탕"), "닭볶음탕", "매운 닭볶음탕");

        RecommendationReply reply = facade.recommend("매운 닭볶음탕 레시피", query);

        assertThat(reply.notices()).containsExactly("말씀하신 닭볶음탕은 찾지 못해서 비슷한 걸 골랐어요");
    }

    @Test
    @DisplayName("말에 있는 키워드가 레시피 이름에 있으면 요리 이름을 채우지 않고 못 찾았다는 안내도 붙이지 않는다")
    void keepsDishTextEmpty_whenSpokenKeywordHasRecipe() {
        givenKeywordCandidates(keywordCandidate(10L, "탕수육", "튀김", "탕수육"));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, null, false, List.of("탕수육"), null);

        RecommendationReply reply = facade.recommend("탕수육 먹고 싶어", query);

        assertThat(reply.notices()).isEmpty();
        assertThat(reply.event().recipes().get(0).reason()).isEqualTo("탕수육 요리예요");
    }

    @Test
    @DisplayName("뺄 재료(알레르기)가 있으면 연결 레시피가 그 재료를 쓰는 밀키트와, 연결이 없는데 이름에 그 재료가 든 밀키트를 뺀다 (땅콩호박은 남김)")
    void dropsMealkitsWithExcludedIngredients() {
        givenMealkits(
                mealkit(40L, "된장국", 9000, 544L),
                mealkit(41L, "땅콩 소스 비빔면", 9000, 543L),
                mealkit(42L, "땅콩 강정", 9000, null),
                mealkit(43L, "땅콩호박 수프", 9000, null));
        when(recipeService.findCandidateIngredients(List.of(101L))).thenReturn(List.of(
                new RecipeCandidateIngredient(543L, 101L, "땅콩", false)));
        when(ingredientService.getIngredientsByIds(List.of(101L))).thenReturn(Map.of(101L, ingredient(101L, "땅콩")));
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, null, null, List.of(101L), false, null, null);

        RecommendationReply reply = facade.recommend("밀키트 추천해줘", query, List.of(), 7L);

        assertThat(reply.event().mealkits()).extracting(m -> m.product().productId())
                .contains(40L, 43L).doesNotContain(41L, 42L);
    }

    @Test
    @DisplayName("\"밀키트 추천해줘\"면 레시피 카드 없이 밀키트를 최대 3장 고르고, 글은 AI 없이 서버가 쓰며, 이미 보여 준 밀키트를 읽는다")
    void picksOnlyMealkits_whenMealkitWordSpoken() {
        givenMealkits(
                mealkit(40L, "된장국", 9000, 544L),
                mealkit(41L, "황태해장국", 9000, 543L),
                mealkit(42L, "사과 새우 북엇국", 9000, 542L),
                mealkit(43L, "부추 콩가루 찜", 9000, 539L));
        when(chatMessageService.findRecommendedProductIds(7L)).thenReturn(Set.of(40L));

        RecommendationReply reply = facade.recommend("밀키트 추천해줘", RecipeQuery.of(MEMBER_ID, List.of()), List.of(), 7L);

        assertThat(reply.event().recipes()).isEmpty();
        assertThat(reply.event().empty()).isFalse();
        assertThat(reply.event().mealkits()).hasSize(3);
        assertThat(reply.event().mealkits()).extracting(m -> m.product().productId()).doesNotContain(40L);
        assertThat(reply.event().mealkits()).allSatisfy(m -> assertThat(m.description()).isNull());
        assertThat(reply.fixedReply()).startsWith("지금 살 수 있는 밀키트를 골라 봤어요.");
        verify(recipeCandidateFacade, never()).findCandidates(any());
    }

    @Test
    @DisplayName("\"국물 밀키트\"면 국 분류 레시피와 연결된 밀키트를 먼저 고르고, 서버 글은 조건에 맞춰 골랐다고 쓴다")
    void picksSoupMealkitsFirst_whenSoupCategoryRequested() {
        givenMealkits(
                mealkit(43L, "부추 콩가루 찜", 8000, 539L),
                mealkit(40L, "된장국", 9000, 544L),
                mealkit(44L, "모둠 전골", 7000, null));
        RecipeQuery soup = new RecipeQuery(MEMBER_ID, List.of(), List.of(), List.of("국"), null, List.of(), false,
                List.of("밀키트"), "국물 밀키트");

        RecommendationReply reply = facade.recommend("국물 밀키트", soup, List.of(), null);

        assertThat(reply.event().mealkits()).extracting(m -> m.product().productId()).containsExactly(40L, 43L, 44L);
        assertThat(reply.event().mealkits().get(0).recipeName()).isEqualTo("된장국");
        assertThat(reply.event().mealkits().get(0).servings()).isEqualTo(2);
        assertThat(reply.fixedReply()).startsWith("말씀하신 조건에 맞춰 밀키트를 골라 봤어요.");
    }

    @Test
    @DisplayName("\"간단하게 먹을 거\"면 레시피 카드 3장 아래 카드와 다른 레시피의 밀키트 한 장을 서버 설명과 함께 붙인다")
    void attachesOneMealkitBelowRecipes_whenEasyWordSpoken() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(544L, "된장국", "국", 10, reason(List.of())),
                candidate(20L, "두부조림", "조림", 20, reason(List.of())),
                candidate(30L, "두부전", "전", 15, reason(List.of()))), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
        givenMealkits(
                mealkit(40L, "된장국", 7000, 544L),
                mealkit(41L, "황태해장국", 9000, 543L));

        RecommendationReply reply = facade.recommend("간단하게 먹을 거", RecipeQuery.of(MEMBER_ID, List.of()), List.of(), null);

        assertThat(reply.event().recipes()).hasSize(3);
        assertThat(reply.event().mealkits()).extracting(m -> m.product().productId()).containsExactly(41L);
        assertThat(reply.event().mealkits().get(0).description())
                .isEqualTo("요리하기 번거로우면 황태해장국 밀키트로 간편하게 드셔 보세요.");
        assertThat(reply.fixedReply()).isNull();
        assertThat(reply.candidateInfo()).isNotNull();
    }

    @Test
    @DisplayName("밀키트를 말했는데 판매 중인 밀키트가 없으면 안내를 맨 앞에 두고 레시피 카드 3장을 보여 주며, 키워드에서 밀키트는 뺀다")
    void showsRecipesWithNotice_whenNoMealkitOnSale() {
        when(productService.getOnSaleMealkits()).thenReturn(List.of());
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "두부찌개", "찌개", 25, reason(List.of())),
                candidate(20L, "두부조림", "조림", 20, reason(List.of())),
                candidate(30L, "두부전", "전", 15, reason(List.of()))), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
        RecipeQuery spoken = new RecipeQuery(MEMBER_ID, List.of(), List.of(), List.of(), null, List.of(), false,
                List.of("밀키트"), "밀키트");

        RecommendationReply reply = facade.recommend("밀키트 뭐 있어", spoken, List.of(), null);

        assertThat(reply.event().recipes()).hasSize(3);
        assertThat(reply.event().mealkits()).isEmpty();
        assertThat(reply.notices()).first().isEqualTo(ChatRecommendationFacade.NO_MEALKIT_NOTICE);
        assertThat(reply.fixedReply()).isNull();
        verify(recipeCandidateFacade).findCandidates(argThat(
                query -> query.keywords().isEmpty() && query.keywordText() == null));
        verify(recipeCandidateFacade, never()).describeMealkitRecipes(any(), any());
    }

    @Test
    @DisplayName("밀키트 단어가 없거나 \"밀키트 말고\"면 밀키트를 고르지 않는다")
    void skipsMealkits_whenNoMealkitWordOrNegated() {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(
                candidate(10L, "두부찌개", "찌개", 25, reason(List.of()))), false));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());

        assertThat(facade.recommend("두부로 뭐 먹지", RecipeQuery.of(MEMBER_ID, List.of()), List.of(), 7L)
                .event().mealkits()).isEmpty();
        assertThat(facade.recommend("밀키트 말고 레시피", RecipeQuery.of(MEMBER_ID, List.of()), List.of(), 7L)
                .event().mealkits()).isEmpty();
        verify(productService, never()).getOnSaleMealkits();
        verify(chatMessageService, never()).findRecommendedProductIds(any());
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

    @Test
    @DisplayName("냉장고에 있는 밀키트면 제안 문장에 \"냉장고에 있는\"을 붙이고, 밀키트만 보여 주는 글에 냉장고 밀키트를 먼저 보여 준다고 쓴다")
    void mentionsFridgeMealkit() {
        MealkitWithPrice doenjang = mealkit(40L, "된장국", 7000, 544L);
        MealkitPicker.Picked inFridge = new MealkitPicker.Picked(doenjang.mealkit(), doenjang.price(), null, 0, 2L);

        assertThat(ChatRecommendationFacade.mealkitSuggestion("된장국 밀키트", true))
                .isEqualTo("요리하기 번거로우면 냉장고에 있는 된장국 밀키트로 간편하게 드셔 보세요.");
        assertThat(ChatRecommendationFacade.mealkitOnlyReply(RecipeQuery.of(MEMBER_ID, List.of()), List.of(inFridge)))
                .isEqualTo("지금 살 수 있는 밀키트를 골라 봤어요. 냉장고에 이미 있는 밀키트는 먼저 보여 드려요. "
                        + "카드를 누르면 상품을 볼 수 있고, 장바구니 아이콘으로 바로 담을 수 있어요.");
    }

    @Test
    @DisplayName("밀키트 제안 문장은 이름에 밀키트를 한 번만 붙이고 받침에 맞춰 으로/로를 쓴다")
    void writesMealkitSuggestion_withParticle() {
        assertThat(ChatRecommendationFacade.mealkitSuggestion("닭갈비"))
                .isEqualTo("요리하기 번거로우면 닭갈비 밀키트로 간편하게 드셔 보세요.");
        assertThat(ChatRecommendationFacade.mealkitSuggestion("된장찌개 밀키트 2인분"))
                .isEqualTo("요리하기 번거로우면 된장찌개 밀키트 2인분으로 간편하게 드셔 보세요.");
        assertThat(ChatRecommendationFacade.mealkitSuggestion("소불고기 밀키트 1팩 세트 750g"))
                .endsWith("750g로 간편하게 드셔 보세요.");
    }

    // 판매 중인 밀키트와 판매가, 연결된 레시피(냉장고 기준 계산)를 돌려준다. 냉장고에 밀키트는 없다
    private void givenMealkits(MealkitWithPrice... mealkits) {
        when(productService.getOnSaleMealkits()).thenReturn(Arrays.stream(mealkits).map(MealkitWithPrice::mealkit).toList());
        Map<Long, SellingPrice> prices = new HashMap<>();
        Map<Long, RecipeCandidate> recipes = new HashMap<>();
        for (MealkitWithPrice mealkit : mealkits) {
            prices.put(mealkit.mealkit().getProduct().getId(), mealkit.price());
            Long recipeId = mealkit.mealkit().getRecipeId();
            if (recipeId != null) {
                String category = mealkit.mealkit().getProduct().getName().endsWith("국") ? "국" : "찜";
                recipes.put(recipeId, candidate(recipeId, mealkit.mealkit().getProduct().getName(), category, 20, reason(List.of())));
            }
        }
        when(saleEventReader.findSellingPrices(any())).thenReturn(prices);
        when(recipeCandidateFacade.describeMealkitRecipes(any(), any()))
                .thenReturn(new RecipeCandidateFacade.MealkitRecipes(recipes, Map.of(), Set.of(), FridgeMealkits.NONE));
    }

    private static Ingredient ingredient(Long id, String name) {
        Ingredient ingredient = Ingredient.builder().ingredientCode("ING_" + id).name(name).baseUnit("g").build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private record MealkitWithPrice(MealkitProduct mealkit, SellingPrice price) {
    }

    private static MealkitWithPrice mealkit(long productId, String name, int price, Long recipeId) {
        return mealkitWith(productId, name, price, recipeId, ProductStatus.ON_SALE);
    }

    private static MealkitWithPrice soldOutMealkit(long productId, String name, int price) {
        return mealkitWith(productId, name, price, 539L, ProductStatus.SOLD_OUT);
    }

    private static MealkitWithPrice mealkitWith(long productId, String name, int price, Long recipeId, ProductStatus status) {
        Product product = Product.builder().productType(ProductType.MEALKIT).name(name).price(price)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10).storage(StorageType.REFRIGERATED).shelfDays(3).status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        MealkitProduct mealkit = MealkitProduct.builder().product(product).servings(2).recipeId(recipeId).build();
        return new MealkitWithPrice(mealkit, SellingPrice.of(product, null));
    }

    private void givenKeywordCandidates(RecipeCandidate... candidates) {
        when(recipeCandidateFacade.findCandidates(any())).thenReturn(new RecipeCandidates(List.of(candidates), true));
        when(recipeService.findSummaries(any())).thenReturn(Map.of());
    }

    // 늘린 이름(키워드)으로 맞은 후보 (요청 일치 1)
    private static RecipeCandidate keywordCandidate(Long id, String name, String category, String keyword) {
        return new RecipeCandidate(id, name, category, 30, new Ranking(1, 0, 0, 0, 0, 3, 3), expanded(keyword));
    }

    // 늘린 이름(키워드)으로 맞은 후보 근거
    private static Reason expanded(String keyword) {
        return new Reason(List.of(), List.of(), List.of(), List.of(), List.of("키워드: 늘린 이름"), false, true, List.of(),
                List.of(), List.of(), new KeywordMatch(KeywordMatch.Type.EXPANDED, keyword, null));
    }

    // 장볼 재료만 있는 근거
    private static Reason missing(MissingIngredient... ingredients) {
        return new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(ingredients),
                List.of(), List.of(), null);
    }

    // 상품 조회는 받은 연결을 돌려주고, 판매가는 상품마다 계산해 둔 값을 돌려준다
    private void givenProducts(ProductWithPrice... products) {
        when(productService.getPublicIngredientProducts(any()))
                .thenReturn(Arrays.stream(products).map(ProductWithPrice::link).toList());
        Map<Long, SellingPrice> prices = new HashMap<>();
        for (ProductWithPrice product : products) {
            prices.put(product.link().getProduct().getId(), product.price());
        }
        when(saleEventReader.findSellingPrices(any())).thenReturn(prices);
    }

    private record ProductWithPrice(IngredientProduct link, SellingPrice price) {
    }

    private static ProductWithPrice product(long productId, long ingredientId, String name, int price, Integer salePrice) {
        Product product = productEntity(productId, name, price, ProductStatus.ON_SALE);
        ActiveSale sale = salePrice == null ? null
                : new ActiveSale(productId, 900L, SaleEventType.SPECIAL, salePrice, 25, LocalDateTime.now().plusDays(1), null, null);
        return new ProductWithPrice(link(product, ingredientId), SellingPrice.of(product, sale));
    }

    private static ProductWithPrice soldOutProduct(long productId, long ingredientId, String name, int price) {
        Product product = productEntity(productId, name, price, ProductStatus.SOLD_OUT);
        return new ProductWithPrice(link(product, ingredientId), SellingPrice.of(product, null));
    }

    private static Product productEntity(long productId, String name, int price, ProductStatus status) {
        Product product = Product.builder().productType(ProductType.INGREDIENT).name(name).price(price)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10).storage(StorageType.REFRIGERATED).shelfDays(7).status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }

    private static IngredientProduct link(Product product, long ingredientId) {
        return IngredientProduct.builder().product(product).ingredientId(ingredientId).unitAmount(BigDecimal.ONE).unit("개")
                .baseQuantity(BigDecimal.ONE).build();
    }

    private static RecipeCandidate candidate(Long id, String name, String category, Integer cookTimeMin, Reason reason) {
        return new RecipeCandidate(id, name, category, cookTimeMin, new Ranking(0, 0, 0, 0, 1, 1, 0), reason);
    }

    private static Reason reason(List<Taste> tastes) {
        return new Reason(List.of(), List.of(), tastes, List.of(), List.of(), false, false, List.of(), List.of(), List.of(), null);
    }

    // 요청 일치 수만 정한 후보
    private static RecipeCandidate requested(Long id, String name, String category, int requestMatches) {
        return new RecipeCandidate(id, name, category, 20, new Ranking(requestMatches, 0, 0, 0, 1, 1, 0), reason(List.of()));
    }
}
