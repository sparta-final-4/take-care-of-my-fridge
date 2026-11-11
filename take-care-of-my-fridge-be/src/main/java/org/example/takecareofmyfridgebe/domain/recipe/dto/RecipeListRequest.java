package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

// keyword, cuisineType, category, maxCookTimeMin, maxDifficulty는 모두 선택이다. 비우면 해당 조건 없이 조회한다.
// keyword는 레시피 이름에 포함되는지 본다. cuisineType과 category는 여러 값을 받을 수 있고(?category=국&category=찌개)
// 그중 하나와 값이 같으면 포함한다. 최댓값 조건은 그 값을 포함한다.
public record RecipeListRequest(
        RecipeSort sort,
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String keyword,
        @Size(max = 20, message = "음식 종류는 20개까지 고를 수 있습니다.")
        List<@Size(max = 20, message = "음식 종류는 20자 이하여야 합니다.") String> cuisineType,
        @Size(max = 50, message = "요리 분류는 50개까지 고를 수 있습니다.")
        List<@Size(max = 50, message = "요리 분류는 50자 이하여야 합니다.") String> category,

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
        cuisineType = normalize(cuisineType);
        category = normalize(category);
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    // 빈 값은 빼고 앞뒤 공백을 지운 뒤 중복을 없앤다. 고른 값이 없으면 빈 목록이다
    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(RecipeListRequest::blankToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
