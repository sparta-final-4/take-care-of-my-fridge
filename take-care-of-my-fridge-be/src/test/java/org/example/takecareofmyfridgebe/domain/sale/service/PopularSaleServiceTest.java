package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleProduct;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 순위 규칙만 본다. 진행 중 할인 조회는 SaleEventItemRepositoryTest가 확인한다.
class PopularSaleServiceTest {

    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 1, 18, 0);

    private final PopularSaleService popularSaleService = new PopularSaleService(mock(SaleEventReader.class));

    @Test
    @DisplayName("최근 검색 클릭이 많은 순으로 나오고, 클릭 기록이 없는 상품은 0으로 쳐서 맨 뒤로 간다")
    void rank_byClicks() {
        // given
        List<Product> products = products(1L, 2L, 3L);
        Map<Long, ActiveSale> sales = sales(special(1L, 20), special(2L, 20), special(3L, 20));
        Map<Long, Long> clicks = Map.of(1L, 3L, 2L, 5L);

        // when
        List<PopularSaleProduct> ranked = popularSaleService.rank(products, sales, clicks);

        // then
        assertThat(productIds(ranked)).containsExactly(2L, 1L, 3L);
    }

    @Test
    @DisplayName("클릭 수가 같으면 할인율이 높은 순이고, 할인율도 같으면 상품 ID 순이다")
    void rank_tieBreak() {
        // given
        List<Product> products = products(1L, 2L, 3L);
        Map<Long, ActiveSale> sales = sales(special(1L, 10), special(2L, 30), special(3L, 30));
        Map<Long, Long> clicks = Map.of(1L, 4L, 2L, 4L, 3L, 4L);

        // when
        List<PopularSaleProduct> ranked = popularSaleService.rank(products, sales, clicks);

        // then
        assertThat(productIds(ranked)).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("상품이 품절이거나 번개 드롭 남은 수량이 0이면 클릭이 많아도 빠진다")
    void rank_excludesUnbuyable() {
        // given
        Product soldOut = ProductFixture.createProduct(1L, "두부", 2000);
        soldOut.decreaseStock(10);
        Product exhausted = ProductFixture.createProduct(2L, "대파 1단", 2900);
        Product onSale = ProductFixture.createProduct(3L, "양파 1kg", 3900);
        Map<Long, ActiveSale> sales = sales(special(1L, 20), flashDrop(2L, 50, 0), flashDrop(3L, 50, 3));
        Map<Long, Long> clicks = Map.of(1L, 10L, 2L, 10L);

        // when
        List<PopularSaleProduct> ranked = popularSaleService.rank(List.of(soldOut, exhausted, onSale), sales, clicks);

        // then
        assertThat(productIds(ranked)).containsExactly(3L);
    }

    @Test
    @DisplayName("후보가 6개를 넘으면 순위가 높은 6개만 남는다")
    void rank_limitsToSix() {
        // given
        List<Product> products = products(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        Map<Long, ActiveSale> sales = sales(LongStream.rangeClosed(1, 8)
                .mapToObj(id -> special(id, 20))
                .toArray(ActiveSale[]::new));
        // 상품 ID가 클수록 클릭이 많다
        Map<Long, Long> clicks = LongStream.rangeClosed(1, 8).boxed()
                .collect(Collectors.toMap(Function.identity(), Function.identity()));

        // when
        List<PopularSaleProduct> ranked = popularSaleService.rank(products, sales, clicks);

        // then
        assertThat(productIds(ranked)).containsExactly(8L, 7L, 6L, 5L, 4L, 3L);
    }

    private static List<Product> products(Long... ids) {
        return Arrays.stream(ids)
                .map(id -> ProductFixture.createProduct(id, "상품 " + id, 3000))
                .toList();
    }

    private static Map<Long, ActiveSale> sales(ActiveSale... sales) {
        return Arrays.stream(sales).collect(Collectors.toMap(ActiveSale::productId, Function.identity()));
    }

    private static ActiveSale special(Long productId, int discountRate) {
        return new ActiveSale(productId, productId * 100, SaleEventType.SPECIAL, 3000 * (100 - discountRate) / 100,
                discountRate, ENDS_AT, null, null);
    }

    private static ActiveSale flashDrop(Long productId, int discountRate, int remaining) {
        return new ActiveSale(productId, productId * 100, SaleEventType.FLASH_DROP, 3000 * (100 - discountRate) / 100,
                discountRate, ENDS_AT, remaining, 10);
    }

    private static List<Long> productIds(List<PopularSaleProduct> ranked) {
        return ranked.stream().map(popular -> popular.product().getId()).toList();
    }
}
