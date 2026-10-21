package org.example.takecareofmyfridgebe.domain.cooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MyRecipeSearchRequest(
        MyRecipeReviewStatus reviewStatus,

        MyRecipeSortType sort,

        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {
    public MyRecipeSearchRequest {
        reviewStatus = reviewStatus == null ? MyRecipeReviewStatus.ALL : reviewStatus;
        sort = sort == null ? MyRecipeSortType.RECENT : sort;
        page = page == null ? 1 : page;
        size = size == null ? 10 : size;
    }
}
