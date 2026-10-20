package org.example.takecareofmyfridgebe.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;

public record ReviewCreateRequest(
        @NotNull(message = "레시피 ID는 필수입니다.")
        Long recipeId,

        @NotNull(message = "별점은 필수입니다.")
        @Min(value = 1, message = "별점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "별점은 5 이하여야 합니다.")
        Integer rating,

        @NotBlank(message = "후기 내용을 입력해 주세요.")
        @Size(max = 100, message = "후기 내용은 100자 이하여야 합니다.")
        String content
) {
    public RecipeReview toEntity(Member member) {
        return RecipeReview.builder()
                .member(member)
                .recipeId(this.recipeId)
                .rating(this.rating)
                .content(this.content)
                .build();
    }
}
