package org.example.takecareofmyfridgebe.dummy;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCreateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepRequest;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

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

    public static RecipeCreateRequest createRecipeRequest(Long... ingredientIds) {
        List<RecipeIngredientRequest> ingredients = Arrays.stream(ingredientIds)
                .map(id -> new RecipeIngredientRequest(id, "재료" + id, "1큰술", null, "주재료", null))
                .toList();
        return new RecipeCreateRequest("된장찌개", "구수한 찌개", null, "한식", "국", "끓이기", null,
                30, 2, 3, null, null, null, ingredients,
                List.of(new RecipeStepRequest("물을 끓인다.", null, null), new RecipeStepRequest("된장을 푼다.", null, null)));
    }
}
