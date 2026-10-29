package org.example.takecareofmyfridgebe.domain.deal.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate.ExcludeReason;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.stereotype.Component;

@Component
public class DealExclusionPolicy {

    public static final int MIN_STOCK = 50;

    public Map<Long, ExcludeReason> excludedProducts(
            List<DealProductInput> products, Map<Long, DealSalesSummary> sales) {
        List<DealProductInput> stocked = products.stream()
                .filter(product -> product.status() != ProductStatus.STOPPED)
                .filter(product -> product.currentStock() >= MIN_STOCK)
                .toList();
        int topCount = (stocked.size() + 9) / 10;
        Set<Long> topSellers = stocked.stream()
                .filter(product -> purchases(sales, product.productId()) > 0)
                .sorted(Comparator.<DealProductInput>comparingInt(
                        product -> purchases(sales, product.productId())).reversed()
                        .thenComparing(DealProductInput::productId))
                .limit(topCount)
                .map(DealProductInput::productId)
                .collect(Collectors.toSet());
        Map<Long, ExcludeReason> excluded = new HashMap<>();
        for (DealProductInput product : products) {
            if (product.status() == ProductStatus.STOPPED) {
                continue;
            }
            if (product.currentStock() < MIN_STOCK) {
                excluded.put(product.productId(), ExcludeReason.LOW_STOCK);
            } else if (topSellers.contains(product.productId())) {
                excluded.put(product.productId(), ExcludeReason.TOP_SELLER_ON_DAY);
            }
        }
        return Map.copyOf(excluded);
    }

    private int purchases(Map<Long, DealSalesSummary> sales, Long productId) {
        DealSalesSummary summary = sales.get(productId);
        return summary == null ? 0 : summary.purchases();
    }
}
