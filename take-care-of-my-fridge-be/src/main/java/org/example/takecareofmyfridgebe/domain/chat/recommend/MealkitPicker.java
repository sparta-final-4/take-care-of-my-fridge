package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상담에 보여 줄 밀키트를 고른다. 스프링에 의존하지 않는 순수 클래스.
 * <p>
 * 거르기: 살 수 있는 것만(판매가가 있고 품절 아님. 냉장고에 있는 밀키트는 품절이어도 둔다), 레시피 카드와 같은 레시피는 빼고,
 * 연결된 레시피가 피할 맛이면 뺀다. 피할 맛이 매운맛이면 매울 수 있는 레시피(색인의 매운 정도 MILD·HOT)도 뺀다.
 * <p>
 * 정렬: 요청 조건 일치 수(분류·원하는 맛·요리 이름 키워드) → 냉장고에 있는 밀키트 → 이 상담에서 아직 안 보여 준 것 → 레시피 연결 있음 →
 * 냉장고 재료와 겹치는 수(레시피 재료 기준) → 할인 중 → 판매 단가 낮은 순 → 상품 ID.
 * 냉장고에 있는 밀키트는 빼지 않고 요청 조건 다음으로 앞에 둔다 ("국물 밀키트"면 국물 밀키트가 먼저, 조건 없이 "밀키트 추천해줘"면 냉장고 밀키트가 맨 앞).
 * 냉장고에 있는지는 fridge.product_id로 보고, product_id가 없는 예전 줄은 등록 이름(raw_name, 공백 무관)으로 본다. 기한이 지난 줄은 넣지 않는다.
 * 안 보여 준 것을 레시피 연결보다 앞에 둔다: "밀키트 뭐 있어"를 다시 물으면 레시피 연결이 없는 밀키트라도 새 것을 먼저 보여 준다.
 * 레시피 연결이 없거나 연결된 레시피가 지워진 밀키트는 요청 조건을 상품 이름 키워드로만 센다.
 * 레시피 연결도 없고 요청 조건도 안 맞는 밀키트는 {@value #MAX_UNRELATED}장까지만 고르고(냉장고에 있는 밀키트는 세지 않는다), 3장이 안 차면 빈자리로 둔다.
 * 기준이 바뀌면 일반 답 프롬프트(prompts/chat-general.txt)의 추천 기준 문장도 같이 고친다.
 */
public final class MealkitPicker {

    private static final Comparator<Option> ORDER = Comparator.comparingInt(Option::requestMatches).reversed()
            .thenComparing((Option option) -> !option.inFridge())
            .thenComparing(Option::shown)
            .thenComparing((Option option) -> option.recipe() == null)
            .thenComparing(Comparator.comparingInt(Option::ownedCount).reversed())
            .thenComparing((Option option) -> option.price().sale().isEmpty())
            .thenComparingInt(option -> option.price().unitPrice())
            .thenComparing(option -> option.mealkit().getProduct().getId());

    // 레시피 연결도 없고 요청 조건도 안 맞는 밀키트를 보여 줄 최대 수. 어떤 요리인지 몰라 고른 이유를 댈 수 없어 한 장으로 줄인다
    static final int MAX_UNRELATED = 1;

    private MealkitPicker() {
    }

    /**
     * 고를 재료.
     *
     * @param mealkits           판매 중인 밀키트 (상품이 함께 읽혀 있어야 한다)
     * @param prices             상품 ID별 판매가. 판매가가 없는 밀키트는 고르지 않는다
     * @param recipes            레시피 ID별 지금 냉장고 기준 계산 (가진 재료 수). 없으면 레시피 연결이 없는 것으로 본다
     * @param recipeTastes       레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
     * @param maybeSpicyRecipeIds 매울 수 있는 레시피 ID (매운 정도 MILD·HOT). "매운 거 말고"면 뺀다
     * @param query              추천 조건 (분류·원하는 맛·피할 맛·키워드)
     * @param cardRecipeIds      같은 답의 레시피 카드 (같은 레시피 밀키트는 뺀다)
     * @param fridgeMealkits     냉장고에 있는 밀키트 (기한 지난 줄 제외)
     * @param shownProductIds    이 상담에서 이미 보여 준 밀키트 상품 ID (뒤로 보낸다)
     */
    public record Input(
            List<MealkitProduct> mealkits,
            Map<Long, SellingPrice> prices,
            Map<Long, RecipeCandidate> recipes,
            Map<Long, Set<Member.Taste>> recipeTastes,
            Set<Long> maybeSpicyRecipeIds,
            RecipeQuery query,
            Collection<Long> cardRecipeIds,
            FridgeMealkits fridgeMealkits,
            Collection<Long> shownProductIds
    ) {
    }

    /**
     * 냉장고에 있는 밀키트 (기한이 지난 줄은 넣지 않는다). 같은 밀키트 줄이 여럿이면 소비기한이 가장 가까운 줄의 남은 일수를 둔다.
     *
     * @param dDayByProductId 상품 ID별 남은 일수 (fridge.product_id가 있는 줄)
     * @param dDayByName      등록 이름(공백 뺀 것)별 남은 일수 (product_id가 없는 예전 줄)
     */
    public record FridgeMealkits(Map<Long, Long> dDayByProductId, Map<String, Long> dDayByName) {
        public static final FridgeMealkits NONE = new FridgeMealkits(Map.of(), Map.of());

        public FridgeMealkits {
            dDayByProductId = dDayByProductId == null ? Map.of() : Map.copyOf(dDayByProductId);
            dDayByName = dDayByName == null ? Map.of() : Map.copyOf(dDayByName);
        }

        // 이 밀키트가 냉장고에 있으면 남은 일수 (상품 ID → 등록 이름 순으로 본다). 없으면 null
        public Long dDayOf(Product product) {
            Long byId = dDayByProductId.get(product.getId());
            return byId != null ? byId : dDayByName.get(ChatTexts.removeWhitespace(product.getName()));
        }
    }

    /**
     * @param recipe         연결된 레시피의 지금 냉장고 기준 계산. 연결이 없으면 null
     * @param requestMatches 맞은 요청 조건 수 (0이면 조건과 상관없이 고른 것)
     * @param fridgeDDay     냉장고에 있으면 남은 일수, 없으면 null
     */
    public record Picked(MealkitProduct mealkit, SellingPrice price, RecipeCandidate recipe, int requestMatches, Long fridgeDDay) {

        public Picked(MealkitProduct mealkit, SellingPrice price, RecipeCandidate recipe, int requestMatches) {
            this(mealkit, price, recipe, requestMatches, null);
        }

        public boolean inFridge() {
            return fridgeDDay != null;
        }
    }

    public static List<Picked> pick(Input input, int limit) {
        Set<Long> cardRecipeIds = Set.copyOf(input.cardRecipeIds());
        Set<Long> shown = Set.copyOf(input.shownProductIds());
        RecipeQuery query = input.query();
        List<Option> sorted = input.mealkits().stream()
                .filter(mealkit -> mealkit.getRecipeId() == null || !cardRecipeIds.contains(mealkit.getRecipeId()))
                .map(mealkit -> toOption(mealkit, input, shown))
                // 냉장고에 있는 밀키트는 사지 않아도 되므로 품절이어도 둔다
                .filter(option -> option.price() != null && (option.inFridge() || !option.price().soldOut()))
                .filter(option -> !avoided(option, input, query))
                .sorted(ORDER)
                .toList();
        List<Picked> picked = new ArrayList<>();
        int unrelated = 0;
        for (Option option : sorted) {
            if (picked.size() >= limit) {
                break;
            }
            // 레시피 연결도 없고 요청 조건도 안 맞는 밀키트는 한 장까지만 (나머지 자리는 비운다). 냉장고에 있는 밀키트는 세지 않는다
            if (option.recipe() == null && option.requestMatches() == 0 && !option.inFridge()) {
                if (unrelated >= MAX_UNRELATED) {
                    continue;
                }
                unrelated++;
            }
            picked.add(new Picked(option.mealkit(), option.price(), option.recipe(), option.requestMatches(), option.fridgeDDay()));
        }
        return List.copyOf(picked);
    }

    private static Option toOption(MealkitProduct mealkit, Input input, Set<Long> shown) {
        Product product = mealkit.getProduct();
        RecipeCandidate recipe = mealkit.getRecipeId() == null ? null : input.recipes().get(mealkit.getRecipeId());
        Set<Member.Taste> tastes = recipe == null ? Set.of() : input.recipeTastes().getOrDefault(recipe.recipeId(), Set.of());
        return new Option(mealkit, input.prices().get(product.getId()), recipe,
                requestMatches(product, recipe, tastes, input.query()), shown.contains(product.getId()),
                input.fridgeMealkits().dDayOf(product));
    }

    // 분류 일치 1 + 원하는 맛 겹친 수 + 키워드가 레시피(없으면 상품) 이름에 있으면 1
    private static int requestMatches(Product product, RecipeCandidate recipe, Set<Member.Taste> tastes, RecipeQuery query) {
        int matches = 0;
        if (recipe != null && recipe.category() != null && query.moodCategories().contains(recipe.category())) {
            matches++;
        }
        matches += (int) query.wantedTastes().stream().filter(tastes::contains).count();
        String name = ChatTexts.removeWhitespace(recipe != null && recipe.name() != null ? recipe.name() : product.getName());
        if (query.keywords().stream().map(ChatTexts::removeWhitespace).anyMatch(keyword -> !keyword.isEmpty() && name.contains(keyword))) {
            matches++;
        }
        return matches;
    }

    // 연결 레시피가 없는 밀키트는 맛을 몰라 거르지 않는다
    private static boolean avoided(Option option, Input input, RecipeQuery query) {
        if (option.recipe() == null || query.avoidTastes().isEmpty()) {
            return false;
        }
        Long recipeId = option.recipe().recipeId();
        if (query.avoidTastes().contains(Member.Taste.SPICY) && input.maybeSpicyRecipeIds().contains(recipeId)) {
            return true;
        }
        Set<Member.Taste> tastes = input.recipeTastes().getOrDefault(recipeId, Set.of());
        return query.avoidTastes().stream().anyMatch(tastes::contains);
    }

    private record Option(MealkitProduct mealkit, SellingPrice price, RecipeCandidate recipe, int requestMatches,
                          boolean shown, Long fridgeDDay) {

        int ownedCount() {
            return recipe == null ? 0 : recipe.ranking().ownedCount();
        }

        boolean inFridge() {
            return fridgeDDay != null;
        }
    }
}
