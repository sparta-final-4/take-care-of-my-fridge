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
        long selected = candidates.stream()
                .filter(item -> item.reviewStatus() == DealCandidate.ReviewStatus.SELECTED).count();
        long held = candidates.stream()
                .filter(item -> item.reviewStatus() == DealCandidate.ReviewStatus.HELD).count();
        return new DealAnalysisResponse(analysis.getId(), analysis.getTargetWeekStart(),
                analysis.getDayOfWeek(), analysis.getDiscountRate(), analysis.getMinStock(),
                analysis.getModelVersion(), analysis.getDataCutoffAt(), analysis.getCreatedAt(),
                analysis.getLastSavedAt(), analysis.isSyntheticData(),
                new ReviewCounts((int) selected, (int) held, candidates.size() - (int) selected - (int) held),
                candidates, excluded);
    }

    public record ReviewCounts(int selected, int held, int unreviewed) {}

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
