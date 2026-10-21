package org.example.takecareofmyfridgebe.dummy;

import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

public class RecipeFixture {

    public static Recipe createRecipe(Long id, String name) {
        Recipe recipe = Recipe.builder()
                .source("MFDS")
                .sourceId("R" + id)
                .name(name)
                .summary(name + " 만드는 법")
                .cuisineType("한식")
                .category("국")
                .cookTimeMin(30)
                .servings(2)
                .difficulty(3)
                .calories(new BigDecimal("350.00"))
                .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    public static RecipeIngredient createIngredient(Recipe recipe, int seq, String displayName, String amount) {
        return RecipeIngredient.builder()
                .recipe(recipe)
                .ingredientId((long) seq)
                .seq(seq)
                .displayName(displayName)
                .amount(amount)
                .role("주재료")
                .build();
    }

    public static RecipeStep createStep(Recipe recipe, int stepNo, String description) {
        return RecipeStep.builder()
                .recipe(recipe)
                .stepNo(stepNo)
                .description(description)
                .build();
    }
}
