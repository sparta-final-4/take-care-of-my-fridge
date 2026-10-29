package org.example.takecareofmyfridgebe.domain.deal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate.ExcludeReason;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealCandidateCalculator {

    private final DealExclusionPolicy exclusionPolicy;

    public List<DealCandidate> calculate(DealAnalysis analysis, List<DealProductInput> products,
            Map<Long, DealSalesSummary> sales, Map<Long, Long> clicks, DealDiscountModel model) {
        Map<Long, ExcludeReason> excluded = exclusionPolicy.excludedProducts(products, sales);
        List<DealCandidate> candidates = new ArrayList<>();
        for (DealProductInput product : products) {
            if (product.status() == ProductStatus.STOPPED) {
                continue;
            }
            DealSalesSummary summary = sales.getOrDefault(product.productId(), new DealSalesSummary(0, 0));
            int searchClicks = Math.toIntExact(clicks.getOrDefault(product.productId(), 0L));
            ExcludeReason reason = excluded.get(product.productId());
            DealDiscountModel.Estimate estimate = reason == null
                    ? model.estimate(summary.avgSalesOnDay(), analysis.getDiscountRate(), product.currentStock())
                    : null;
            int discountPrice = (int) ((long) product.regularPrice()
                    * (100 - analysis.getDiscountRate()) / 100);
            candidates.add(DealCandidate.builder()
                    .dealAnalysis(analysis)
                    .productId(product.productId())
                    .productName(product.productName())
                    .regularPrice(product.regularPrice())
                    .discountPrice(discountPrice)
                    .currentStock(product.currentStock())
                    .avgSalesOnDay(summary.avgSalesOnDay())
                    .searchClicks(searchClicks)
                    .purchases(summary.purchases())
                    .excluded(reason != null)
                    .excludeReason(reason)
                    .candidateReason(reason == null ? searchClicks > summary.purchases()
                            ? "검색 클릭이 순판매 수량보다 많음" : "재고·판매 기준 통과" : null)
                    .expectedQtyRegular(estimate == null ? null : estimate.expectedRegular())
                    .expectedQtyDiscount(estimate == null ? null : estimate.expectedDiscount())
                    .expectedIncrease(estimate == null ? null : estimate.expectedIncrease())
                    .build());
        }
        return candidates;
    }
}
