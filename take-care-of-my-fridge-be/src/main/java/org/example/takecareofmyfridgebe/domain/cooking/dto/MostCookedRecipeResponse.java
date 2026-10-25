package org.example.takecareofmyfridgebe.domain.cooking.dto;

public record MostCookedRecipeResponse(
        Long recipeId,
        String recipeName,
        long cookCount
) {
}
