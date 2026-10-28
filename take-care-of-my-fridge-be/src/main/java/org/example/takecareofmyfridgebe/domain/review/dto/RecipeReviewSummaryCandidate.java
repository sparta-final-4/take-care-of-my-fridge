package org.example.takecareofmyfridgebe.domain.review.dto;

import java.time.LocalDateTime;

public record RecipeReviewSummaryCandidate(
        Long recipeId,
        long reviewCount,
        LocalDateTime latestReviewUpdatedAt
) {
}
