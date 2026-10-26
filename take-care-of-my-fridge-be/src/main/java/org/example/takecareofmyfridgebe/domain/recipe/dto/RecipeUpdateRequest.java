package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;

import java.util.List;
import java.util.stream.IntStream;

// 보낸 필드만 바꾼다(null = 기존 값 유지). 선택 텍스트 필드는 빈 문자열을 보내면 비운다.
// 재료(ingredients)와 조리 단계(steps)는 보내면 목록 전체를 교체하고, 순서 번호는 목록 순서대로 1부터 다시 매긴다.
public record RecipeUpdateRequest(
        @Size(min = 1, max = 200, message = "레시피 이름은 1자 이상 200자 이하여야 합니다.")
        String name,

        @Size(max = RecipeCreateRequest.MAX_TEXT_LENGTH, message = "요약은 2,000자 이하여야 합니다.")
        String summary,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.")
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

        @Min(value = 1, message = "난이도는 1 이상이어야 합니다.")
        @Max(value = 10, message = "난이도는 10 이하여야 합니다.")
        Integer difficulty,

        @Size(max = 255, message = "태그는 255자 이하여야 합니다.")
        String tags,

        @Size(max = RecipeCreateRequest.MAX_TEXT_LENGTH, message = "팁은 2,000자 이하여야 합니다.")
        String tip,

        @Valid
        RecipeNutritionRequest nutrition,

        @Size(min = 1, max = 100, message = "재료는 1개 이상 100개 이하로 입력해 주세요.")
        List<@NotNull(message = "재료 항목이 비어 있습니다.") @Valid RecipeIngredientRequest> ingredients,

        @Size(min = 1, max = 50, message = "조리 단계는 1개 이상 50개 이하로 입력해 주세요.")
        List<@NotNull(message = "조리 단계 항목이 비어 있습니다.") @Valid RecipeStepRequest> steps
) {
    public RecipeUpdateRequest {
        name = strip(name);
        imageUrl = strip(imageUrl);
    }

    public boolean replacesIngredients() {
        return ingredients != null;
    }

    public boolean replacesSteps() {
        return steps != null;
    }

    public List<Long> ingredientIds() {
        return ingredients.stream().map(RecipeIngredientRequest::ingredientId).distinct().toList();
    }

    public void applyTo(Recipe recipe) {
        recipe.changeBasicInfo(
                orCurrent(name, recipe.getName()),
                patchText(summary, recipe.getSummary()),
                patchText(imageUrl, recipe.getImageUrl()),
                patchText(tags, recipe.getTags()),
                patchText(tip, recipe.getTip())
        );
        recipe.changeClassification(
                patchText(cuisineType, recipe.getCuisineType()),
                patchText(category, recipe.getCategory()),
                patchText(cookMethod, recipe.getCookMethod()),
                patchText(mainIngredientCategory, recipe.getMainIngredientCategory())
        );
        recipe.changeCookingInfo(
                orCurrent(cookTimeMin, recipe.getCookTimeMin()),
                orCurrent(servings, recipe.getServings()),
                orCurrent(difficulty, recipe.getDifficulty())
        );
        applyNutritionTo(recipe);
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

    // 영양 정보는 보낸 항목만 바꾼다.
    private void applyNutritionTo(Recipe recipe) {
        if (nutrition == null) {
            return;
        }
        recipe.changeNutrition(
                orCurrent(nutrition.weightG(), recipe.getWeightG()),
                orCurrent(nutrition.calories(), recipe.getCalories()),
                orCurrent(nutrition.carbsG(), recipe.getCarbsG()),
                orCurrent(nutrition.proteinG(), recipe.getProteinG()),
                orCurrent(nutrition.fatG(), recipe.getFatG()),
                orCurrent(nutrition.sodiumMg(), recipe.getSodiumMg())
        );
    }

    // 기존 값이 비어 있을 수 있어 requireNonNullElse 대신 쓴다.
    private static <T> T orCurrent(T value, T current) {
        return value != null ? value : current;
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    // null이면 기존 값 유지, 빈 문자열이면 비운다.
    private static String patchText(String value, String current) {
        if (value == null) {
            return current;
        }
        return value.isBlank() ? null : value;
    }
}
