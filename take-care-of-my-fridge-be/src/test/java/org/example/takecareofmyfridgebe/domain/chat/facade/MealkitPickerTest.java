package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.MealkitPicker.Input;
import org.example.takecareofmyfridgebe.domain.chat.facade.MealkitPicker.Picked;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MealkitPickerTest {

    private static final Long MEMBER_ID = 1L;

    private final List<MealkitProduct> mealkits = new ArrayList<>();
    private final Map<Long, SellingPrice> prices = new HashMap<>();
    private final Map<Long, RecipeCandidate> recipes = new HashMap<>();
    private final Map<Long, Set<Taste>> tastes = new HashMap<>();
    private final Set<Long> maybeSpicy = new HashSet<>();

    @Test
    @DisplayName("요청 분류(국)에 맞는 레시피와 연결된 밀키트를 먼저 고르고, 레시피 연결이 없는 밀키트는 맨 뒤에 둔다")
    void picksCategoryMatchFirst_andNoRecipeLast() {
        mealkit(40L, "모둠 전골", 9000, null);
        mealkit(41L, "부추 콩가루 찜", 12000, recipe(539L, "부추 콩가루 찜", "찜", 3));
        mealkit(42L, "된장국", 15000, recipe(544L, "된장국", "국", 0));

        List<Picked> picked = MealkitPicker.pick(input(query(List.of("국"), List.of()), Set.of(), Set.of(), Set.of()), 3);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(42L, 41L, 40L);
        assertThat(picked.get(0).requestMatches()).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 조건이 같으면 냉장고 재료와 많이 겹치는 것 → 할인 중 → 판매 단가 낮은 순으로 고른다")
    void ordersByOwnedThenSaleThenPrice() {
        mealkit(40L, "된장국", 9000, recipe(544L, "된장국", "국", 1));
        mealkit(41L, "황태해장국", 12000, recipe(543L, "황태해장국", "국", 1), sale(41L, 11000));
        mealkit(42L, "사과 새우 북엇국", 8000, recipe(542L, "사과 새우 북엇국", "국", 1));
        mealkit(43L, "부추 콩가루 찜", 20000, recipe(539L, "부추 콩가루 찜", "찜", 4));

        List<Picked> picked = MealkitPicker.pick(input(query(List.of(), List.of()), Set.of(), Set.of(), Set.of()), 4);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(43L, 41L, 42L, 40L);
    }

    @Test
    @DisplayName("연결된 레시피가 피할 맛이면 빼고, 원하는 맛과 요리 이름 키워드가 맞으면 요청 일치로 센다")
    void skipsAvoidedTaste_andCountsWantedTasteAndKeyword() {
        mealkit(40L, "얼큰 육개장", 9000, recipe(600L, "얼큰 육개장", "국", 5));
        tastes.put(600L, Set.of(Taste.SPICY));
        mealkit(41L, "된장국", 12000, recipe(544L, "된장국", "국", 0));
        mealkit(42L, "황태해장국", 12000, recipe(543L, "황태해장국", "국", 0));
        tastes.put(543L, Set.of(Taste.SWEET));

        RecipeQuery avoidSpicy = query(List.of(), List.of()).withAvoidTastes(List.of(Taste.SPICY));
        assertThat(MealkitPicker.pick(input(avoidSpicy, Set.of(), Set.of(), Set.of()), 3))
                .extracting(p -> p.mealkit().getProduct().getId()).doesNotContain(40L);

        RecipeQuery sweetOrDoenjang = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SWEET), List.of(), null, List.of(),
                false, List.of("된장국"), null);
        assertThat(MealkitPicker.pick(input(sweetOrDoenjang, Set.of(), Set.of(), Set.of()), 3))
                .extracting(p -> p.mealkit().getProduct().getId(), Picked::requestMatches)
                .contains(tuple(41L, 1), tuple(42L, 1), tuple(40L, 0))
                .last().isEqualTo(tuple(40L, 0));
    }

    @Test
    @DisplayName("\"매운 거 말고\"면 맛 데이터에 매운맛이 없어도 연결 레시피가 매울 수 있음(MILD·HOT)인 밀키트를 빼고, 연결 레시피가 없는 밀키트는 남긴다")
    void skipsMaybeSpicyRecipeMealkit_whenAvoidingSpicy() {
        mealkit(27L, "부추 콩가루 찜 밀키트", 9000, recipe(539L, "부추 콩가루 찜", "찜", 3));
        tastes.put(539L, Set.of(Taste.MILD));
        maybeSpicy.add(539L);
        mealkit(30L, "고추잡채 밀키트", 9000, recipe(59L, "고추잡채", "볶음", 3));
        tastes.put(59L, Set.of(Taste.SALTY));
        maybeSpicy.add(59L);
        mealkit(24L, "된장국 밀키트", 9000, recipe(544L, "된장국", "국", 0));
        tastes.put(544L, Set.of(Taste.UMAMI));
        mealkit(13L, "나베", 9000, null);

        RecipeQuery avoidSpicy = query(List.of(), List.of()).withAvoidTastes(List.of(Taste.SPICY));
        List<Picked> picked = MealkitPicker.pick(input(avoidSpicy, Set.of(), Set.of(), Set.of()), 3);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(24L, 13L);
    }

    @Test
    @DisplayName("\"매운 걸로\"면 맛에 매운맛이 있는(HOT) 레시피 밀키트만 원하는 맛과 맞은 것으로 세고, 매울 수 있음(MILD)은 세지 않는다")
    void countsOnlyHotAsWantedSpicy() {
        mealkit(27L, "부추 콩가루 찜 밀키트", 9000, recipe(539L, "부추 콩가루 찜", "찜", 0));
        tastes.put(539L, Set.of(Taste.MILD, Taste.SPICY));
        maybeSpicy.add(539L);
        mealkit(30L, "고추잡채 밀키트", 9000, recipe(59L, "고추잡채", "볶음", 0));
        tastes.put(59L, Set.of(Taste.SALTY));
        maybeSpicy.add(59L);

        RecipeQuery wantSpicy = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), List.of(), null, List.of(),
                false, List.of(), null);
        List<Picked> picked = MealkitPicker.pick(input(wantSpicy, Set.of(), Set.of(), Set.of()), 3);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId(), Picked::requestMatches)
                .containsExactly(tuple(27L, 1), tuple(30L, 0));
    }

    @Test
    @DisplayName("품절·판매가 없는 밀키트, 레시피 카드와 같은 레시피, 냉장고에 이름이 같은 밀키트는 빼고 이미 보여 준 밀키트는 뒤로 보낸다")
    void skipsUnavailableAndDuplicates_andMovesShownBack() {
        mealkit(40L, "된장국", 9000, recipe(544L, "된장국", "국", 5));
        mealkit(41L, "황태 해장국", 9000, recipe(543L, "황태해장국", "국", 5));
        mealkit(42L, "사과 새우 북엇국", 9000, recipe(542L, "사과 새우 북엇국", "국", 5), null, ProductStatus.SOLD_OUT);
        mealkit(43L, "부추 콩가루 찜", 9000, recipe(539L, "부추 콩가루 찜", "찜", 5));
        mealkit(44L, "닭가슴살 브로콜리 만두", 9000, recipe(555L, "닭가슴살 브로콜리 만두", "만두", 1));
        mealkit(45L, "판매가 없음", 9000, recipe(556L, "판매가 없음", "국", 5));
        prices.remove(45L);

        List<Picked> picked = MealkitPicker.pick(
                input(query(List.of(), List.of()), Set.of(544L), Set.of("황태해장국"), Set.of(43L)), 3);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(44L, 43L);
    }

    @Test
    @DisplayName("이 상담에서 안 보여 준 밀키트를 레시피 연결보다 먼저 고른다 (두 번째 \"밀키트 뭐 있어\"에 연결 없는 나베가 이미 보여 준 된장국보다 앞)")
    void picksUnshownBeforeRecipeLinked() {
        mealkit(13L, "나베", 9000, null);
        mealkit(24L, "된장국 밀키트", 9000, recipe(544L, "된장국", "국", 3));
        mealkit(25L, "황태해장국 밀키트", 9000, recipe(543L, "황태해장국", "국", 1));
        mealkit(28L, "닭가슴살 브로콜리 만두 밀키트", 9000, recipe(555L, "닭가슴살 브로콜리 만두", "만두", 2));

        List<Picked> picked = MealkitPicker.pick(
                input(query(List.of(), List.of()), Set.of(), Set.of(), Set.of(24L)), 3);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(28L, 25L, 13L);
    }

    @Test
    @DisplayName("요청 조건 일치는 안 보여 준 것보다 앞선다 (국을 말하면 이미 보여 준 국 밀키트가 안 보여 준 다른 밀키트보다 앞)")
    void putsRequestMatchBeforeUnshown() {
        mealkit(13L, "나베", 9000, null);
        mealkit(24L, "된장국 밀키트", 9000, recipe(544L, "된장국", "국", 0));

        List<Picked> picked = MealkitPicker.pick(
                input(query(List.of("국"), List.of()), Set.of(), Set.of(), Set.of(24L)), 3);

        assertThat(picked).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(24L, 13L);
    }

    @Test
    @DisplayName("레시피 연결도 없고 요청 조건도 안 맞는 밀키트는 한 장까지만 고르고 남는 자리는 비우며, 이름이 요청과 맞으면 이 제한을 받지 않는다")
    void picksAtMostOneUnrelatedMealkit() {
        mealkit(24L, "된장국 밀키트", 9000, recipe(544L, "된장국", "국", 3));
        mealkit(29L, "부하 테스트용 상품1", 1000, null);
        mealkit(31L, "부하 테스트용 분산 상품 01", 1000, null);
        mealkit(32L, "부하 테스트용 분산 상품 02", 1000, null);

        List<Picked> shownAll = MealkitPicker.pick(
                input(query(List.of(), List.of()), Set.of(), Set.of(), Set.of(24L)), 3);
        List<Picked> named = MealkitPicker.pick(
                input(query(List.of(), List.of("분산")), Set.of(), Set.of(), Set.of()), 3);

        assertThat(shownAll).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(29L, 24L);
        assertThat(named).extracting(p -> p.mealkit().getProduct().getId()).containsExactly(31L, 32L, 24L);
    }

    private Input input(RecipeQuery query, Set<Long> cardRecipeIds, Set<String> fridgeNames, Set<Long> shown) {
        return new Input(mealkits, prices, recipes, tastes, maybeSpicy, query, cardRecipeIds, fridgeNames, shown);
    }

    private static RecipeQuery query(List<String> categories, List<String> keywords) {
        return new RecipeQuery(MEMBER_ID, List.of(), List.of(), categories, null, List.of(), false, keywords, null);
    }

    private RecipeCandidate recipe(Long id, String name, String category, int owned) {
        RecipeCandidate candidate = new RecipeCandidate(id, name, category, 20, new Ranking(0, 0, 0, 0, owned, 6, 6 - owned),
                new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(), List.of(),
                        List.of(), null));
        recipes.put(id, candidate);
        return candidate;
    }

    private void mealkit(Long productId, String name, int price, RecipeCandidate recipe) {
        mealkit(productId, name, price, recipe, null, ProductStatus.ON_SALE);
    }

    private void mealkit(Long productId, String name, int price, RecipeCandidate recipe, ActiveSale sale) {
        mealkit(productId, name, price, recipe, sale, ProductStatus.ON_SALE);
    }

    private void mealkit(Long productId, String name, int price, RecipeCandidate recipe, ActiveSale sale, ProductStatus status) {
        Product product = Product.builder()
                .productType(ProductType.MEALKIT)
                .name(name)
                .price(price)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(3)
                .status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        prices.put(productId, SellingPrice.of(product, sale));
        mealkits.add(MealkitProduct.builder()
                .product(product)
                .servings(2)
                .recipeId(recipe == null ? null : recipe.recipeId())
                .build());
    }

    private static ActiveSale sale(long productId, int salePrice) {
        return new ActiveSale(productId, 900L + productId, SaleEventType.SPECIAL, salePrice, 10,
                LocalDateTime.now().plusDays(1), null, null);
    }
}
