package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate.ExcludeReason;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;

class DealCandidateCalculatorTest {

    private final DealCandidateCalculator calculator = new DealCandidateCalculator(new DealExclusionPolicy());

    @Test
    void keepsEveryEligibleProductAndStoresExclusionReasonsAndEstimates() {
        DealAnalysis analysis = DealAnalysis.builder().discountRate(30).build();
        DealDiscountModel model = DealDiscountModel.train(List.of(
                new DealDiscountModel.Observation(10, 10, 12),
                new DealDiscountModel.Observation(20, 10, 14),
                new DealDiscountModel.Observation(30, 10, 16)));
        List<DealCandidate> result = calculator.calculate(analysis, List.of(
                product(1L, 20, ProductStatus.ON_SALE),
                product(2L, 100, ProductStatus.ON_SALE),
                product(3L, 100, ProductStatus.ON_SALE),
                product(4L, 100, ProductStatus.STOPPED)),
                Map.of(1L, new DealSalesSummary(1, 1),
                       2L, new DealSalesSummary(20, 5),
                       3L, new DealSalesSummary(4, 2)),
                Map.of(3L, 10L), model);

        assertThat(result).extracting(DealCandidate::getProductId).containsExactly(1L, 2L, 3L);
        assertThat(result.get(0).getExcludeReason()).isEqualTo(ExcludeReason.LOW_STOCK);
        assertThat(result.get(1).getExcludeReason()).isEqualTo(ExcludeReason.TOP_SELLER_ON_DAY);
        assertThat(result.get(0).getExpectedQtyDiscount()).isNull();
        DealCandidate candidate = result.get(2);
        assertThat(candidate.isExcluded()).isFalse();
        assertThat(candidate.getDiscountPrice()).isEqualTo(700);
        assertThat(candidate.getExpectedQtyRegular()).isEqualTo(2);
        assertThat(candidate.getExpectedQtyDiscount()).isEqualTo(3);
        assertThat(candidate.getExpectedIncrease()).isEqualTo(1);
        assertThat(candidate.getSearchClicks()).isEqualTo(10);
        assertThat(candidate.getPurchases()).isEqualTo(4);
    }

    private DealProductInput product(Long id, int stock, ProductStatus status) {
        return new DealProductInput(id, "상품 " + id, 1000, status, stock);
    }
}
