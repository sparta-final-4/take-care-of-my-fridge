package org.example.takecareofmyfridgebe.domain.recipe.event;

public record RecipeChangedEvent(
        Long recipeId
) {

    public static RecipeChangedEvent from(Long recipeId) {
        return new RecipeChangedEvent(recipeId);
    }

}
