package org.example.takecareofmyfridgebe.domain.recipe.dto;

public record RecipeSummary(
        Long recipeId,
        String name,
        String imageUrl
) {}
