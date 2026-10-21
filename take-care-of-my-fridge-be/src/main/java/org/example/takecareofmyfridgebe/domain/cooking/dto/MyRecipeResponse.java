package org.example.takecareofmyfridgebe.domain.cooking.dto;

import java.time.LocalDateTime;

public record MyRecipeResponse(
        Long recipeId,
        String recipeName,
        String recipeImageUrl,
        long cookCount,
        LocalDateTime lastCookedAt,
        boolean fromChat,
        MyRecipeReviewResponse review
) {
    /**
     * QueryDSL Projections.constructor 전용 생성자.
     */
    public MyRecipeResponse(
            Long recipeId,
            String recipeName,
            String recipeImageUrl,
            Long cookCount,
            LocalDateTime lastCookedAt,
            Boolean fromChat,
            Long reviewId,
            Integer reviewRating,
            String reviewContent,
            LocalDateTime reviewUpdatedAt
    ) {
        this(
                recipeId,
                recipeName,
                recipeImageUrl,
                cookCount != null ? cookCount : 0L,
                lastCookedAt,
                Boolean.TRUE.equals(fromChat),
                reviewId != null
                        ? new MyRecipeReviewResponse(reviewId, reviewRating != null ? reviewRating : 0, reviewContent, reviewUpdatedAt)
                        : null
        );
    }
}
