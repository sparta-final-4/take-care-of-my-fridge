package org.example.takecareofmyfridgebe.domain.recipe.dto;

public record RecipeIngredientNameDto(
        Long recipeId,
        String name,
        String role
) {}
