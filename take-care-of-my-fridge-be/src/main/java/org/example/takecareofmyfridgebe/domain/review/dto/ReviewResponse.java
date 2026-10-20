package org.example.takecareofmyfridgebe.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long recipeId,
        int rating,
        String content,
        String nickname,
        boolean mine,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime updatedAt
) {
    public static ReviewResponse from(RecipeReview review, Long currentMemberId) {
        Long authorId = review.getMember() != null ? review.getMember().getId() : null;
        boolean isMine = currentMemberId != null && currentMemberId.equals(authorId);
        String nickname = review.getMember() != null ? review.getMember().getNickname() : null;

        return new ReviewResponse(
                review.getId(),
                review.getRecipeId(),
                review.getRating(),
                review.getContent(),
                nickname,
                isMine,
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
