package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.recommend.MealkitPicker;
import org.example.takecareofmyfridgebe.domain.chat.reply.RecommendationReasonWriter;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지난 대화의 추천 카드(레시피·밀키트)를 지금 레시피·상품·냉장고 기준으로 다시 만든다. 상담 메시지 기록 조회(ChatMessageQueryFacade)가 쓴다.
 * 여러 도메인(냉장고·레시피·상품·검색)을 조회해서 facade 층에 둔다.
 */
@Component
@RequiredArgsConstructor
public class ChatCardRebuilder {

    private final RecipeCardDescriber recipeCardDescriber;
    private final RecipeService recipeService;
    private final ProductService productService;
    private final SaleEventReader saleEventReader;

    /**
     * 저장된 추천 카드를 다시 만든다. 순서·설명은 저장값, 이름·이미지·분류·시간은 지금 레시피, 임박·장볼 재료·이유는 지금 냉장고 기준.
     * 지워진 레시피는 빼고, "냉장고에 없는 선택 재료"는 고른 재료를 저장하지 않으므로 비운다. 레시피 조회는 메시지 수와 상관없이 한 번씩이다.
     *
     * 밀키트 카드는 빼고 레시피 카드만 만든다 (밀키트는 rebuildMealkitCards).
     *
     * @return AI 메시지 ID별 카드 (보여 준 순서)
     */
    public Map<Long, List<RecommendedRecipe>> rebuildCards(Long memberId, List<SavedRecommendation> all) {
        List<SavedRecommendation> saved = all.stream().filter(recommendation -> !recommendation.isProduct()).toList();
        if (saved.isEmpty()) {
            return Map.of();
        }
        List<Long> recipeIds = saved.stream().map(SavedRecommendation::recipeId).distinct().toList();
        Map<Long, RecipeCandidate> candidates = recipeCardDescriber.describeRecipes(memberId, recipeIds);
        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(recipeIds);
        // 상품·판매가는 메시지 수와 상관없이 한 번씩, 지금 가격·품절 기준
        Map<Long, ShoppingProduct> products = RecommendationCards.shoppingProducts(candidates.values(), productService,
                saleEventReader);

        Map<Long, List<SavedRecommendation>> byMessage = saved.stream()
                .collect(Collectors.groupingBy(SavedRecommendation::messageId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<RecommendedRecipe>> cards = new LinkedHashMap<>();
        byMessage.forEach((messageId, recommendations) -> {
            List<SavedRecommendation> alive = recommendations.stream()
                    .sorted(Comparator.comparingInt(SavedRecommendation::displayOrder))
                    .filter(recommendation -> candidates.containsKey(recommendation.recipeId()))
                    .toList();
            List<RecipeCandidate> picked = alive.stream().map(r -> candidates.get(r.recipeId())).toList();
            List<String> reasons = RecommendationReasonWriter.writeDistinct(picked, null);
            List<RecommendedRecipe> messageCards = new ArrayList<>();
            for (int i = 0; i < alive.size(); i++) {
                RecipeCandidate candidate = picked.get(i);
                messageCards.add(RecommendationCards.toCard(candidate, summaries.get(candidate.recipeId()), reasons.get(i),
                        alive.get(i).description(), products));
            }
            cards.put(messageId, messageCards);
        });
        return cards;
    }

    /**
     * 저장된 밀키트 카드를 다시 만든다. 순서·설명은 저장값, 이름·이미지·인분·가격·품절은 지금 상품, 레시피 이름은 지금 레시피 기준.
     * 판매 중지됐거나 지워진 밀키트는 빼고, 품절이면 카드는 두고 품절로 보여 준다.
     * "냉장고에 있어요"(inFridge·남은 일수)는 지금 냉장고 기준이다.
     * 상품 카드 중 밀키트(상품 종류 MEALKIT)만 만든다 (재료 상품 카드는 ChatFridgeShopFacade.rebuildProductCards).
     * 상품·판매가·레시피 이름·냉장고 조회는 메시지 수와 상관없이 한 번씩이고, 밀키트 카드가 없으면 조회하지 않는다.
     *
     * @return AI 메시지 ID별 밀키트 카드 (보여 준 순서). 밀키트 카드가 없는 메시지는 맵에 없다
     */
    public Map<Long, List<RecommendedMealkit>> rebuildMealkitCards(Long memberId, List<SavedRecommendation> all) {
        List<SavedRecommendation> saved = all.stream().filter(SavedRecommendation::isProduct).toList();
        if (saved.isEmpty()) {
            return Map.of();
        }
        Map<Long, MealkitProduct> mealkits = productService.getPublicMealkits(
                        saved.stream().map(SavedRecommendation::productId).distinct().toList()).stream()
                .collect(Collectors.toMap(mealkit -> mealkit.getProduct().getId(), Function.identity()));
        if (mealkits.isEmpty()) {
            return Map.of();
        }
        Map<Long, SellingPrice> prices = saleEventReader.findSellingPrices(
                mealkits.values().stream().map(MealkitProduct::getProduct).toList());
        Map<Long, RecipeSummary> recipes = recipeService.findSummaries(
                mealkits.values().stream().map(MealkitProduct::getRecipeId).filter(Objects::nonNull).distinct().toList());
        MealkitPicker.FridgeMealkits fridge = recipeCardDescriber.findFridgeMealkits(memberId);

        Map<Long, List<RecommendedMealkit>> cards = new LinkedHashMap<>();
        saved.stream()
                .sorted(Comparator.comparing(SavedRecommendation::messageId)
                        .thenComparingInt(SavedRecommendation::displayOrder))
                .forEach(recommendation -> {
                    MealkitProduct mealkit = mealkits.get(recommendation.productId());
                    SellingPrice price = mealkit == null ? null : prices.get(recommendation.productId());
                    if (price == null) {
                        return;
                    }
                    RecipeSummary recipe = mealkit.getRecipeId() == null ? null : recipes.get(mealkit.getRecipeId());
                    cards.computeIfAbsent(recommendation.messageId(), id -> new ArrayList<>())
                            .add(RecommendationCards.toMealkitCard(mealkit, price, recipe == null ? null : recipe.name(),
                                    recommendation.description(), fridge.dDayOf(mealkit.getProduct())));
                });
        return cards;
    }
}
