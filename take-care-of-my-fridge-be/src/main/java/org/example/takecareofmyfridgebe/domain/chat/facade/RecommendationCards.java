package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.TasteLabel;
import org.example.takecareofmyfridgebe.domain.chat.recommend.ShoppingProductPicker;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추천 카드(레시피·밀키트)를 만든다. 새 추천(ChatRecommendationFacade)과 지난 대화 카드 다시 만들기(ChatCardRebuilder)가 같이 쓴다.
 */
final class RecommendationCards {

    private RecommendationCards() {
    }

    /**
     * 카드들의 장볼 재료마다 담을 상품. 장볼 재료 ID를 모아 상품 조회 한 번, 판매가 계산 한 번만 한다.
     * 장볼 재료가 없거나 판매 상품이 없으면 조회를 줄이고 빈 맵을 돌려준다.
     *
     * @return 재료 ID별 담을 상품
     */
    static Map<Long, ShoppingProduct> shoppingProducts(Collection<RecipeCandidate> cards, ProductService productService,
                                                       SaleEventReader saleEventReader) {
        Set<Long> ingredientIds = cards.stream()
                .flatMap(card -> card.reason().missingIngredients().stream())
                .map(RecipeCandidate.MissingIngredient::ingredientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        List<IngredientProduct> links = productService.getPublicIngredientProducts(ingredientIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        return ShoppingProductPicker.pick(links,
                saleEventReader.findSellingPrices(links.stream().map(IngredientProduct::getProduct).distinct().toList()));
    }

    static RecommendedMealkit toMealkitCard(MealkitProduct mealkit, SellingPrice price, String recipeName,
                                            String description, Long fridgeDDay) {
        return new RecommendedMealkit(ShoppingProduct.from(mealkit.getProduct(), price), mealkit.getServings(),
                mealkit.getRecipeId(), recipeName, description, fridgeDDay != null, fridgeDDay);
    }

    static RecommendedRecipe toCard(RecipeCandidate candidate, RecipeSummary summary, String cardReason,
                                    String description, Map<Long, ShoppingProduct> products) {
        Reason reason = candidate.reason();
        // 원한 맛을 말했으면 그 맛, 아니면 회원 선호 맛과 겹친 맛
        List<Member.Taste> tastes = reason.matchedWantedTastes().isEmpty()
                ? reason.matchedTastes()
                : reason.matchedWantedTastes();
        return new RecommendedRecipe(
                candidate.recipeId(),
                candidate.name(),
                summary == null ? null : summary.imageUrl(),
                candidate.category(),
                candidate.cookTimeMin(),
                reason.usedFridgeIngredients(),
                reason.imminentIngredients().stream()
                        .map(imminent -> new ImminentIngredient(imminent.name(), imminent.dDay()))
                        .toList(),
                tastes.stream().map(TasteLabel::of).toList(),
                reason.missingIngredients().stream()
                        .map(missing -> new MissingIngredient(missing.ingredientId(), missing.name(),
                                products.get(missing.ingredientId())))
                        .toList(),
                reason.missingSelectedIngredients(),
                reason.lacksManyIngredients(),
                cardReason,
                description
        );
    }
}
