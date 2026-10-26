package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;

import java.util.List;
import java.util.stream.IntStream;

// 재료 순서(seq)와 조리 단계 번호(stepNo)는 목록 순서대로 1부터 매긴다.
public record RecipeCreateRequest(
        @NotBlank(message = "레시피 이름을 입력해 주세요.")
        @Size(max = 200, message = "레시피 이름은 200자 이하여야 합니다.")
        String name,

        @Size(max = MAX_TEXT_LENGTH, message = "요약은 2,000자 이하여야 합니다.")
        String summary,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^https?://\\S+$", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.")
        String imageUrl,

        @Size(max = 20, message = "음식 종류는 20자 이하여야 합니다.")
        String cuisineType,

        @Size(max = 50, message = "요리 분류는 50자 이하여야 합니다.")
        String category,

        @Size(max = 20, message = "조리 방법은 20자 이하여야 합니다.")
        String cookMethod,

        @Size(max = 30, message = "주재료 분류는 30자 이하여야 합니다.")
        String mainIngredientCategory,

        @Min(value = 1, message = "조리 시간은 1분 이상이어야 합니다.")
        Integer cookTimeMin,

        @Min(value = 1, message = "분량은 1인분 이상이어야 합니다.")
        Integer servings,

        @NotNull(message = "난이도를 입력해 주세요.")
        @Min(value = 1, message = "난이도는 1 이상이어야 합니다.")
        @Max(value = 10, message = "난이도는 10 이하여야 합니다.")
        Integer difficulty,

        @Size(max = 255, message = "태그는 255자 이하여야 합니다.")
        String tags,

        @Size(max = MAX_TEXT_LENGTH, message = "팁은 2,000자 이하여야 합니다.")
        String tip,

        @Valid
        RecipeNutritionRequest nutrition,

        @NotEmpty(message = "재료를 하나 이상 입력해 주세요.")
        @Size(max = 100, message = "재료는 100개까지 입력할 수 있습니다.")
        List<@NotNull(message = "재료 항목이 비어 있습니다.") @Valid RecipeIngredientRequest> ingredients,

        @NotEmpty(message = "조리 단계를 하나 이상 입력해 주세요.")
        @Size(max = 50, message = "조리 단계는 50개까지 입력할 수 있습니다.")
        List<@NotNull(message = "조리 단계 항목이 비어 있습니다.") @Valid RecipeStepRequest> steps
) {
    public static final int MAX_TEXT_LENGTH = 2_000;

    // 관리자가 직접 등록한 레시피의 출처 값
    public static final String SOURCE_ADMIN = "ADMIN";

    public RecipeCreateRequest {
        name = strip(name);
        imageUrl = blankToNull(strip(imageUrl));
    }

    public List<Long> ingredientIds() {
        return ingredients.stream().map(RecipeIngredientRequest::ingredientId).distinct().toList();
    }

    public Recipe toEntity(String sourceId) {
        RecipeNutritionRequest recipeNutrition = nutrition;
        if (nutrition == null) {
            recipeNutrition = new RecipeNutritionRequest(null, null, null, null, null, null);
        }
        return Recipe.builder()
                .source(SOURCE_ADMIN)
                .sourceId(sourceId)
                .name(name)
                .summary(summary)
                .imageUrl(imageUrl)
                .cuisineType(cuisineType)
                .category(category)
                .cookMethod(cookMethod)
                .mainIngredientCategory(mainIngredientCategory)
                .cookTimeMin(cookTimeMin)
                .servings(servings)
                .difficulty(difficulty)
                .tags(tags)
                .tip(tip)
                .weightG(recipeNutrition.weightG())
                .calories(recipeNutrition.calories())
                .carbsG(recipeNutrition.carbsG())
                .proteinG(recipeNutrition.proteinG())
                .fatG(recipeNutrition.fatG())
                .sodiumMg(recipeNutrition.sodiumMg())
                .build();
    }

    public List<RecipeIngredient> toIngredients(Recipe recipe) {
        return IntStream.range(0, ingredients.size())
                .mapToObj(index -> ingredients.get(index).toEntity(recipe, index + 1))
                .toList();
    }

    public List<RecipeStep> toSteps(Recipe recipe) {
        return IntStream.range(0, steps.size())
                .mapToObj(index -> steps.get(index).toEntity(recipe, index + 1))
                .toList();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
