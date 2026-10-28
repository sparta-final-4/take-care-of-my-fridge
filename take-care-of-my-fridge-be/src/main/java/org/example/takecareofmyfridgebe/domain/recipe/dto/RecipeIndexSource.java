package org.example.takecareofmyfridgebe.domain.recipe.dto;

import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RecipeIndexSource(
        Long id,
        String source,
        String sourceId,
        String name,
        String summary,
        String cuisineType,
        String category,
        String cookMethod,
        String mainIngredientCategory,
        Integer cookTimeMin,
        Integer servings,
        int difficulty,
        BigDecimal weightG,
        BigDecimal calories,
        BigDecimal carbsG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sodiumMg,
        String tags,
        String tip,
        String imageUrl,
        LocalDateTime createdAt,
        List<Ingredient> ingredients,
        List<String> steps
) {

    public record Ingredient(
            String name,
            String role
    ) {
    }

    public static RecipeIndexSource from(Recipe recipe, List<Ingredient> ingredients, List<String> steps) {
        return new RecipeIndexSource(
                recipe.getId(),
                recipe.getSource(),
                recipe.getSourceId(),
                recipe.getName(),
                recipe.getSummary(),
                recipe.getCuisineType(),
                recipe.getCategory(),
                recipe.getCookMethod(),
                recipe.getMainIngredientCategory(),
                recipe.getCookTimeMin(),
                recipe.getServings(),
                recipe.getDifficulty(),
                recipe.getWeightG(),
                recipe.getCalories(),
                recipe.getCarbsG(),
                recipe.getProteinG(),
                recipe.getFatG(),
                recipe.getSodiumMg(),
                recipe.getTags(),
                recipe.getTip(),
                recipe.getImageUrl(),
                recipe.getCreatedAt(),
                ingredients,
                steps
        );
    }
}