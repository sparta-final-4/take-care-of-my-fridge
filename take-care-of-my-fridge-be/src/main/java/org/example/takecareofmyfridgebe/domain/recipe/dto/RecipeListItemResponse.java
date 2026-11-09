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
        BigDecimal calories,
        // 마지막 조리 단계의 사진. 없으면 null (카드 hover 때만 쓴다)
        String lastStepImageUrl
) {
    public static RecipeListItemResponse from(Recipe recipe, String lastStepImageUrl) {
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
                .lastStepImageUrl(lastStepImageUrl)
                .build();
    }
}
