package org.example.takecareofmyfridgebe.domain.recipe.dto;

import java.util.List;

// 레시피 목록 필터 칩에 쓰는 분류 값. 레시피 데이터에 실제로 있는 값만 가나다순으로 담는다.
public record RecipeClassificationResponse(
        List<String> cuisineTypes,
        List<String> categories
) {
}
