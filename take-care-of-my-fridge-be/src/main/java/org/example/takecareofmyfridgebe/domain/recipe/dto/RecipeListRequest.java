package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// keyword, cuisineType, category, maxCookTimeMin, maxDifficulty는 모두 선택이다. 비우면 해당 조건 없이 조회한다.
// keyword는 레시피 이름에 포함되는지, cuisineType과 category는 값이 같은지 본다. 최댓값 조건은 그 값을 포함한다.
public record RecipeListRequest(
        RecipeSort sort,
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String keyword,
        @Size(max = 20, message = "음식 종류는 20자 이하여야 합니다.")
        String cuisineType,
        @Size(max = 50, message = "요리 분류는 50자 이하여야 합니다.")
        String category,

        @Min(value = 1, message = "최대 조리 시간은 1분 이상이어야 합니다.")
        Integer maxCookTimeMin,

        @Min(value = 1, message = "최대 난이도는 1 이상이어야 합니다.")
        @Max(value = 10, message = "최대 난이도는 10 이하여야 합니다.")
        Integer maxDifficulty,

        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public RecipeListRequest {
        sort = sort == null ? RecipeSort.LATEST : sort;
        keyword = blankToNull(keyword);
        cuisineType = blankToNull(cuisineType);
        category = blankToNull(category);
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
