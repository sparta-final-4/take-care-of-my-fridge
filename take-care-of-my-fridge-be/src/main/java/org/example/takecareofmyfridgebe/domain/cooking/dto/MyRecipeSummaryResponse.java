package org.example.takecareofmyfridgebe.domain.cooking.dto;

public record MyRecipeSummaryResponse(
        long recipeCount,
        long totalCookCount,
        long writtenCount,
        long notWrittenCount,
        MostCookedRecipeResponse mostCookedRecipe,
        long consumedIngredientCount
) {
    public static MyRecipeSummaryResponse empty() {
        return new MyRecipeSummaryResponse(0L, 0L, 0L, 0L, null, 0L);
    }
}
