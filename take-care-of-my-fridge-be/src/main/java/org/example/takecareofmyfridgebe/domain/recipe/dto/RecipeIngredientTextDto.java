package org.example.takecareofmyfridgebe.domain.recipe.dto;

/**
 * 레시피 재료 한 줄의 이름 두 가지: 재료 사전 이름(ingredients.name)과 레시피에 적힌 이름(recipe_ingredients.display_name).
 * 사전 정리로 홍고추·청고추가 "고추"로 합쳐졌어도 레시피 원문 이름으로 확인할 수 있게 둘 다 담는다.
 *
 * @param ingredientName 재료 사전 이름 (재료가 없으면 null)
 * @param displayName    레시피에 적힌 이름 (없으면 null)
 */
public record RecipeIngredientTextDto(
        Long recipeId,
        String ingredientName,
        String displayName
) {
}
