package org.example.takecareofmyfridgebe.domain.recipe.dto;

/**
 * 후보 검색용: 주어진 재료가 들어간 레시피 재료 한 줄.
 * 같은 레시피에 같은 재료가 여러 번(양념 그룹 등) 나와도 한 번만 담는다.
 */
public record RecipeCandidateIngredient(
        Long recipeId,
        Long ingredientId,
        String ingredientName,
        boolean basicSeasoning
) {}
