package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 상담에 보여 줄 밀키트를 고른다. 스프링에 의존하지 않는 순수 클래스.
 * <p>
 * 거르기: 살 수 있는 것만(판매가가 있고 품절 아님), 레시피 카드와 같은 레시피는 빼고, 냉장고에 이름이 같은 밀키트가 있으면 빼고,
 * 연결된 레시피가 피할 맛이면 뺀다.
 * <p>
 * 정렬: 요청 조건 일치 수(분류·원하는 맛·요리 이름 키워드) → 이 상담에서 아직 안 보여 준 것 → 레시피 연결 있음 →
 * 냉장고 재료와 겹치는 수(레시피 재료 기준) → 할인 중 → 판매 단가 낮은 순 → 상품 ID.
 * 안 보여 준 것을 레시피 연결보다 앞에 둔다: "밀키트 뭐 있어"를 다시 물으면 레시피 연결이 없는 밀키트라도 새 것을 먼저 보여 준다.
 * 레시피 연결이 없거나 연결된 레시피가 지워진 밀키트는 요청 조건을 상품 이름 키워드로만 센다.
 */
public final class MealkitPicker {

    private static final Comparator<Option> ORDER = Comparator.comparingInt(Option::requestMatches).reversed()
            .thenComparing(Option::shown)
            .thenComparing((Option option) -> option.recipe() == null)
            .thenComparing(Comparator.comparingInt(Option::ownedCount).reversed())
            .thenComparing((Option option) -> option.price().sale().isEmpty())
            .thenComparingInt(option -> option.price().unitPrice())
            .thenComparing(option -> option.mealkit().getProduct().getId());

    private MealkitPicker() {
    }

    /**
     * 고를 재료.
     *
     * @param mealkits           판매 중인 밀키트 (상품이 함께 읽혀 있어야 한다)
     * @param prices             상품 ID별 판매가. 판매가가 없는 밀키트는 고르지 않는다
     * @param recipes            레시피 ID별 지금 냉장고 기준 계산 (가진 재료 수). 없으면 레시피 연결이 없는 것으로 본다
     * @param recipeTastes       레시피 ID별 맛
     * @param query              추천 조건 (분류·원하는 맛·피할 맛·키워드)
     * @param cardRecipeIds      같은 답의 레시피 카드 (같은 레시피 밀키트는 뺀다)
     * @param fridgeMealkitNames 냉장고 밀키트 줄 이름 (공백 무관)
     * @param shownProductIds    이 상담에서 이미 보여 준 밀키트 상품 ID (뒤로 보낸다)
     */
    public record Input(
            List<MealkitProduct> mealkits,
            Map<Long, SellingPrice> prices,
            Map<Long, RecipeCandidate> recipes,
            Map<Long, Set<Member.Taste>> recipeTastes,
            RecipeQuery query,
            Collection<Long> cardRecipeIds,
            Collection<String> fridgeMealkitNames,
            Collection<Long> shownProductIds
    ) {
    }

    /**
     * @param recipe        연결된 레시피의 지금 냉장고 기준 계산. 연결이 없으면 null
     * @param requestMatches 맞은 요청 조건 수 (0이면 조건과 상관없이 고른 것)
     */
    public record Picked(MealkitProduct mealkit, SellingPrice price, RecipeCandidate recipe, int requestMatches) {
    }

    public static List<Picked> pick(Input input, int limit) {
        Set<String> fridgeNames = input.fridgeMealkitNames().stream()
                .map(MealkitPicker::compact)
                .collect(Collectors.toSet());
        Set<Long> cardRecipeIds = Set.copyOf(input.cardRecipeIds());
        Set<Long> shown = Set.copyOf(input.shownProductIds());
        RecipeQuery query = input.query();
        return input.mealkits().stream()
                .filter(mealkit -> !fridgeNames.contains(compact(mealkit.getProduct().getName())))
                .filter(mealkit -> mealkit.getRecipeId() == null || !cardRecipeIds.contains(mealkit.getRecipeId()))
                .map(mealkit -> toOption(mealkit, input, shown))
                .filter(option -> option.price() != null && !option.price().soldOut())
                .filter(option -> !avoided(option, input.recipeTastes(), query))
                .sorted(ORDER)
                .limit(limit)
                .map(option -> new Picked(option.mealkit(), option.price(), option.recipe(), option.requestMatches()))
                .toList();
    }

    private static Option toOption(MealkitProduct mealkit, Input input, Set<Long> shown) {
        Product product = mealkit.getProduct();
        RecipeCandidate recipe = mealkit.getRecipeId() == null ? null : input.recipes().get(mealkit.getRecipeId());
        Set<Member.Taste> tastes = recipe == null ? Set.of() : input.recipeTastes().getOrDefault(recipe.recipeId(), Set.of());
        return new Option(mealkit, input.prices().get(product.getId()), recipe,
                requestMatches(product, recipe, tastes, input.query()), shown.contains(product.getId()));
    }

    // 분류 일치 1 + 원하는 맛 겹친 수 + 키워드가 레시피(없으면 상품) 이름에 있으면 1
    private static int requestMatches(Product product, RecipeCandidate recipe, Set<Member.Taste> tastes, RecipeQuery query) {
        int matches = 0;
        if (recipe != null && recipe.category() != null && query.moodCategories().contains(recipe.category())) {
            matches++;
        }
        matches += (int) query.wantedTastes().stream().filter(tastes::contains).count();
        String name = compact(recipe != null && recipe.name() != null ? recipe.name() : product.getName());
        if (query.keywords().stream().map(MealkitPicker::compact).anyMatch(keyword -> !keyword.isEmpty() && name.contains(keyword))) {
            matches++;
        }
        return matches;
    }

    private static boolean avoided(Option option, Map<Long, Set<Member.Taste>> recipeTastes, RecipeQuery query) {
        if (option.recipe() == null || query.avoidTastes().isEmpty()) {
            return false;
        }
        Set<Member.Taste> tastes = recipeTastes.getOrDefault(option.recipe().recipeId(), Set.of());
        return query.avoidTastes().stream().anyMatch(tastes::contains);
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\s+", "");
    }

    private record Option(MealkitProduct mealkit, SellingPrice price, RecipeCandidate recipe, int requestMatches,
                          boolean shown) {

        int ownedCount() {
            return recipe == null ? 0 : recipe.ranking().ownedCount();
        }
    }
}
