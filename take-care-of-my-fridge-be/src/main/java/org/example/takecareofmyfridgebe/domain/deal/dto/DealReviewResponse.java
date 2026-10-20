package org.example.takecareofmyfridgebe.domain.deal.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;

public record DealReviewResponse(
        LocalDateTime lastSavedAt,
        DealAnalysisResponse.ReviewCounts reviewCounts
) {
    public static DealReviewResponse from(LocalDateTime savedAt, List<DealCandidate> candidates) {
        int selected = 0;
        int held = 0;
        int unreviewed = 0;
        for (DealCandidate candidate : candidates) {
            if (candidate.isExcluded()) {
                continue;
            }
            switch (candidate.getReviewStatus()) {
                case SELECTED -> selected++;
                case HELD -> held++;
                case UNREVIEWED -> unreviewed++;
            }
        }
        return new DealReviewResponse(savedAt,
                new DealAnalysisResponse.ReviewCounts(selected, held, unreviewed));
    }
}
