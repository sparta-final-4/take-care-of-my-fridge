package org.example.takecareofmyfridgebe.domain.recipe.dto;

public record RecipeIngredientNameDto(
        Long recipeId,
        Long ingredientId,
        String name,
        String displayName,
        String role,
        boolean basicSeasoning
) {}
