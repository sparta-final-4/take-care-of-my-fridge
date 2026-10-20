package org.example.takecareofmyfridgebe.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewSearchRequest(
        Long recipeId,
        Boolean mine,
        ReviewSortType sort,
        @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
        Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {
    public ReviewSearchRequest {
        if (sort == null) {
            sort = ReviewSortType.LATEST;
        }
        if (page == null) {
            page = 1;
        }
        if (size == null) {
            size = 10;
        }
    }
}
