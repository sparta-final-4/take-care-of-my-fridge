package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingProductPickerTest {

    private static final long GREEN_ONION = 3L;
    private static final long TOFU = 21L;

    private final Map<Long, SellingPrice> prices = new HashMap<>();

    @Test
    @DisplayName("재료에 상품이 하나면 그 상품을 정가·용량·단위와 함께 붙이고, 할인이 없으면 할인가·할인 구분은 비운다")
    void picksOnlyProduct_withListPriceAndUnit() {
        IngredientProduct link = link(16L, GREEN_ONION, "대파 1단", 3000, ProductStatus.ON_SALE, null);

        Map<Long, ShoppingProduct> picked = ShoppingProductPicker.pick(List.of(link), prices);

        assertThat(picked.get(GREEN_ONION)).isEqualTo(new ShoppingProduct(16L, "대파 1단", "https://img/16.jpg", 3000,
                null, null, false, new BigDecimal("1"), "단"));
    }

    @Test
    @DisplayName("상품이 없는 재료는 결과에 없다")
    void omitsIngredient_whenNoProduct() {
        IngredientProduct link = link(16L, GREEN_ONION, "대파 1단", 3000, ProductStatus.ON_SALE, null);

        assertThat(ShoppingProductPicker.pick(List.of(link), prices)).containsOnlyKeys(GREEN_ONION);
    }

    @Test
    @DisplayName("할인 중이면 할인가와 할인 구분을 담는다")
    void fillsSalePrice_whenOnSale() {
        IngredientProduct link = link(21L, TOFU, "부침용 두부 1모", 2000, ProductStatus.ON_SALE, sale(21L, SaleEventType.SPECIAL, 1500, null));

        ShoppingProduct product = ShoppingProductPicker.pick(List.of(link), prices).get(TOFU);

        assertThat(product.price()).isEqualTo(2000);
        assertThat(product.salePrice()).isEqualTo(1500);
        assertThat(product.saleType()).isEqualTo(SaleEventType.SPECIAL);
        assertThat(product.soldOut()).isFalse();
    }

    @Test
    @DisplayName("품절 상품뿐이면 품절로 표시해 붙이고, 번개 드롭 남은 수량이 0이어도 품절이다")
    void marksSoldOut_whenOnlySoldOutProducts() {
        IngredientProduct soldOut = link(16L, GREEN_ONION, "대파 1단", 3000, ProductStatus.SOLD_OUT, null);
        IngredientProduct exhausted = link(21L, TOFU, "두부 1모", 2000, ProductStatus.ON_SALE,
                sale(21L, SaleEventType.FLASH_DROP, 1000, 0));

        Map<Long, ShoppingProduct> picked = ShoppingProductPicker.pick(List.of(soldOut, exhausted), prices);

        assertThat(picked.get(GREEN_ONION).soldOut()).isTrue();
        assertThat(picked.get(TOFU).soldOut()).isTrue();
    }

    @Test
    @DisplayName("재료 하나에 상품이 여러 개면 살 수 있는 것 → 할인 중 → 판매 단가 낮은 순 → 상품 ID 순으로 하나를 고른다")
    void picksBestProduct_whenManyProducts() {
        IngredientProduct soldOutCheap = link(30L, GREEN_ONION, "대파 품절", 500, ProductStatus.SOLD_OUT, null);
        IngredientProduct cheap = link(31L, GREEN_ONION, "대파 싼 것", 1000, ProductStatus.ON_SALE, null);
        IngredientProduct onSale = link(32L, GREEN_ONION, "대파 할인", 3000, ProductStatus.ON_SALE,
                sale(32L, SaleEventType.SPECIAL, 2500, null));
        IngredientProduct sameTofuA = link(41L, TOFU, "두부 A", 2000, ProductStatus.ON_SALE, null);
        IngredientProduct sameTofuB = link(40L, TOFU, "두부 B", 2000, ProductStatus.ON_SALE, null);
        IngredientProduct pricierTofu = link(42L, TOFU, "두부 C", 2500, ProductStatus.ON_SALE, null);

        Map<Long, ShoppingProduct> picked = ShoppingProductPicker.pick(
                List.of(soldOutCheap, cheap, onSale, sameTofuA, sameTofuB, pricierTofu), prices);

        // 대파: 품절(500원)보다 살 수 있는 것, 그중 싼 것(1,000원)보다 할인 중(2,500원)
        assertThat(picked.get(GREEN_ONION).productId()).isEqualTo(32L);
        // 두부: 할인 없이 같은 단가면 상품 ID가 작은 것
        assertThat(picked.get(TOFU).productId()).isEqualTo(40L);
    }

    @Test
    @DisplayName("판매가를 계산하지 못한 상품은 고르지 않는다")
    void skipsProduct_whenNoSellingPrice() {
        IngredientProduct link = link(16L, GREEN_ONION, "대파 1단", 3000, ProductStatus.ON_SALE, null);
        prices.clear();

        assertThat(ShoppingProductPicker.pick(List.of(link), prices)).isEmpty();
    }

    private IngredientProduct link(long productId, long ingredientId, String name, int price, ProductStatus status, ActiveSale sale) {
        Product product = Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .imageUrl("https://img/" + productId + ".jpg")
                .price(price)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        prices.put(productId, SellingPrice.of(product, sale));
        String unit = ingredientId == GREEN_ONION ? "단" : "모";
        return IngredientProduct.builder()
                .product(product)
                .ingredientId(ingredientId)
                .unitAmount(new BigDecimal("1"))
                .unit(unit)
                .baseQuantity(BigDecimal.ONE)
                .build();
    }

    private static ActiveSale sale(long productId, SaleEventType type, int salePrice, Integer remaining) {
        return new ActiveSale(productId, 900L + productId, type, salePrice, 10, LocalDateTime.now().plusDays(1),
                remaining, remaining == null ? null : 10);
    }
}
