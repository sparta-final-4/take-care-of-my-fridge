package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecipeListRequest(
        RecipeSort sort,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public RecipeListRequest {
        sort = sort == null ? RecipeSort.LATEST : sort;
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
