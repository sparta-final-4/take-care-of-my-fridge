package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate.ExcludeReason;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;

class DealExclusionPolicyTest {

    private final DealExclusionPolicy policy = new DealExclusionPolicy();

    @Test
    void excludesLowStockFirstThenHighestSellerAmongRemainingProducts() {
        List<DealProductInput> products = new ArrayList<>();
        products.add(product(1L, 49, ProductStatus.ON_SALE));
        for (long id = 2; id <= 11; id++) {
            products.add(product(id, 50, ProductStatus.ON_SALE));
        }
        products.add(product(12L, 100, ProductStatus.STOPPED));
        Map<Long, DealSalesSummary> sales = Map.of(
                1L, new DealSalesSummary(100, 25),
                2L, new DealSalesSummary(40, 10),
                3L, new DealSalesSummary(30, 8),
                12L, new DealSalesSummary(200, 50));

        assertThat(policy.excludedProducts(products, sales)).isEqualTo(Map.of(
                1L, ExcludeReason.LOW_STOCK,
                2L, ExcludeReason.TOP_SELLER_ON_DAY));
    }

    @Test
    void doesNotCallZeroSalesProductATopSeller() {
        assertThat(policy.excludedProducts(
                List.of(product(1L, 50, ProductStatus.ON_SALE)), Map.of())).isEmpty();
    }

    private DealProductInput product(Long id, int stock, ProductStatus status) {
        return new DealProductInput(id, "상품 " + id, 1000, status, stock);
    }
}
