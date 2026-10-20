package org.example.takecareofmyfridgebe.domain.deal.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;

public record DealReviewResponse(
        LocalDateTime lastSavedAt,
        DealAnalysisResponse.ReviewCounts reviewCounts
) {
    public static DealReviewResponse from(LocalDateTime savedAt, List<DealCandidate> candidates) {
        return new DealReviewResponse(savedAt, DealAnalysisResponse.ReviewCounts.from(candidates));
    }
}
