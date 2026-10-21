package org.example.takecareofmyfridgebe.domain.deal.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;

public record DealAnalysisResponse(
        Long analysisId,
        LocalDate targetWeekStart,
        DealAnalysis.Day dayOfWeek,
        int discountRate,
        int minStock,
        String modelVersion,
        LocalDateTime dataCutoffAt,
        LocalDateTime createdAt,
        LocalDateTime lastSavedAt,
        boolean syntheticData,
        ReviewCounts reviewCounts,
        List<Candidate> candidates,
        List<Excluded> excluded
) {
    public static DealAnalysisResponse from(DealAnalysis analysis, List<DealCandidate> items) {
        List<Candidate> candidates = items.stream()
                .filter(item -> !item.isExcluded())
                .sorted(Comparator.comparing(DealCandidate::getExpectedIncrease,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DealCandidate::getId))
                .map(Candidate::from).toList();
        List<Excluded> excluded = items.stream()
                .filter(DealCandidate::isExcluded)
                .sorted(Comparator.comparing(DealCandidate::getId))
                .map(Excluded::from).toList();
        return new DealAnalysisResponse(analysis.getId(), analysis.getTargetWeekStart(),
                analysis.getDayOfWeek(), analysis.getDiscountRate(), analysis.getMinStock(),
                analysis.getModelVersion(), analysis.getDataCutoffAt(), analysis.getCreatedAt(),
                analysis.getLastSavedAt(), analysis.isSyntheticData(),
                ReviewCounts.from(items),
                candidates, excluded);
    }

    public record ReviewCounts(int selected, int held, int unreviewed) {

        // 제외 상품은 세지 않는다. 검토 상태가 비어 있으면 미검토로 센다.
        public static ReviewCounts from(List<DealCandidate> items) {
            int selected = 0;
            int held = 0;
            int unreviewed = 0;
            for (DealCandidate item : items) {
                if (item.isExcluded()) {
                    continue;
                }
                if (item.getReviewStatus() == DealCandidate.ReviewStatus.SELECTED) {
                    selected++;
                } else if (item.getReviewStatus() == DealCandidate.ReviewStatus.HELD) {
                    held++;
                } else {
                    unreviewed++;
                }
            }
            return new ReviewCounts(selected, held, unreviewed);
        }
    }

    public record Candidate(
            Long candidateId,
            Long productId,
            String productName,
            int regularPrice,
            int discountPrice,
            Integer expectedQtyRegular,
            Integer expectedQtyDiscount,
            Integer expectedIncrease,
            int searchClicks,
            int purchases,
            int currentStock,
            String candidateReason,
            DealCandidate.ReviewStatus reviewStatus,
            DealCandidate.EventRequestStatus eventRequestStatus
    ) {
        private static Candidate from(DealCandidate item) {
            return new Candidate(item.getId(), item.getProductId(), item.getProductName(),
                    item.getRegularPrice(), item.getDiscountPrice(),
                    item.getExpectedQtyRegular(), item.getExpectedQtyDiscount(),
                    item.getExpectedIncrease(), item.getSearchClicks(), item.getPurchases(),
                    item.getCurrentStock(), item.getCandidateReason(),
                    item.getReviewStatus(), item.getEventRequestStatus());
        }
    }

    public record Excluded(
            Long candidateId,
            Long productId,
            String productName,
            DealCandidate.ExcludeReason excludeReason,
            int avgSalesOnDay,
            int currentStock
    ) {
        private static Excluded from(DealCandidate item) {
            return new Excluded(item.getId(), item.getProductId(), item.getProductName(),
                    item.getExcludeReason(), item.getAvgSalesOnDay(), item.getCurrentStock());
        }
    }
}
