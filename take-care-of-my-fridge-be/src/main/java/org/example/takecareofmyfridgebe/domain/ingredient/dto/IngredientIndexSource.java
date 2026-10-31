package org.example.takecareofmyfridgebe.domain.ingredient.dto;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;

// ES 재료 색인용. 이름 정제에 쓰는 표준 재료만 담는다
public record IngredientIndexSource(
        Long id,
        String name
) {
    public static IngredientIndexSource from(Ingredient ingredient) {
        return new IngredientIndexSource(ingredient.getId(), ingredient.getName());
    }
}
