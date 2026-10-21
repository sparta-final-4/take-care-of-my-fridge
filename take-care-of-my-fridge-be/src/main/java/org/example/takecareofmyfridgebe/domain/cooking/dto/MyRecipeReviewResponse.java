package org.example.takecareofmyfridgebe.domain.cooking.dto;

import java.time.LocalDateTime;

public record MyRecipeReviewResponse(
        Long id,
        int rating,
        String content,
        LocalDateTime updatedAt
) {}
