package org.example.takecareofmyfridgebe.domain.recipe.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Builder
public record RecipeDetailResponse(
        Long recipeId,
        String name,
        String summary,
        String imageUrl,
        String cuisineType,
        String category,
        String cookMethod,
        Integer cookTimeMin,
        Integer servings,
        int difficulty,
        String tags,
        String tip,
        String tipSummary,
        Nutrition nutrition,
        List<IngredientItem> ingredients,
        List<StepItem> steps
) {
    // 1인분 기준 영양 정보
    public record Nutrition(
            BigDecimal weightG,
            BigDecimal calories,
            BigDecimal carbsG,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal sodiumMg
    ) {
        public static Nutrition from(Recipe recipe) {
            return new Nutrition(recipe.getWeightG(), recipe.getCalories(), recipe.getCarbsG(),
                    recipe.getProteinG(), recipe.getFatG(), recipe.getSodiumMg());
        }
    }

    public record IngredientItem(
            int seq,
            Long ingredientId,
            String displayName,
            String amount,
            String role,
            String groupName,
            BigDecimal quantity,
            boolean basicSeasoning
    ) {
        public static IngredientItem from(RecipeIngredient ingredient, boolean basicSeasoning) {
            return new IngredientItem(
                    ingredient.getSeq(),
                    ingredient.getIngredientId(),
                    ingredient.getDisplayName(),
                    ingredient.getAmount(),
                    ingredient.getRole(),
                    ingredient.getGroupName(),
                    ingredient.getQuantity(),
                    basicSeasoning
            );
        }
    }

    public record StepItem(
            int stepNo,
            String description,
            String imageUrl,
            String tip
    ) {
        public static StepItem from(RecipeStep step) {
            return new StepItem(step.getStepNo(), step.getDescription(), step.getImageUrl(), step.getTip());
        }
    }

    public static RecipeDetailResponse of(
            Recipe recipe,
            List<RecipeIngredient> ingredients,
            List<RecipeStep> steps,
            Set<Long> basicSeasoningIds
    ) {
        return RecipeDetailResponse.builder()
                .recipeId(recipe.getId())
                .name(recipe.getName())
                .summary(recipe.getSummary())
                .imageUrl(recipe.getImageUrl())
                .cuisineType(recipe.getCuisineType())
                .category(recipe.getCategory())
                .cookMethod(recipe.getCookMethod())
                .cookTimeMin(recipe.getCookTimeMin())
                .servings(recipe.getServings())
                .difficulty(recipe.getDifficulty())
                .tags(recipe.getTags())
                .tip(recipe.getTip())
                .tipSummary(recipe.getTipSummary())
                .nutrition(Nutrition.from(recipe))
                .ingredients(ingredients.stream()
                        .map(ingredient -> IngredientItem.from(
                                ingredient,
                                basicSeasoningIds.contains(ingredient.getIngredientId())
                        ))
                        .toList())
                .steps(steps.stream().map(StepItem::from).toList())
                .build();
    }
}
