package org.example.takecareofmyfridgebe.domain.recipe.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;

import java.math.BigDecimal;

@Builder
public record RecipeListItemResponse(
        Long recipeId,
        String name,
        String summary,
        String imageUrl,
        String cuisineType,
        String category,
        Integer cookTimeMin,
        Integer servings,
        int difficulty,
        BigDecimal calories
) {
    public static RecipeListItemResponse from(Recipe recipe) {
        return RecipeListItemResponse.builder()
                .recipeId(recipe.getId())
                .name(recipe.getName())
                .summary(recipe.getSummary())
                .imageUrl(recipe.getImageUrl())
                .cuisineType(recipe.getCuisineType())
                .category(recipe.getCategory())
                .cookTimeMin(recipe.getCookTimeMin())
                .servings(recipe.getServings())
                .difficulty(recipe.getDifficulty())
                .calories(recipe.getCalories())
                .build();
    }
}
