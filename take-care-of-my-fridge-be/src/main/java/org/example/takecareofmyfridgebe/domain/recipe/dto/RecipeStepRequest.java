package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;

// 레시피 등록, 수정에서 함께 쓰는 조리 단계 항목. 단계 번호(stepNo)는 목록 위치로 정한다.
public record RecipeStepRequest(
        @NotBlank(message = "조리 단계 설명을 입력해 주세요.")
        @Size(max = RecipeCreateRequest.MAX_TEXT_LENGTH, message = "조리 단계 설명은 2,000자 이하여야 합니다.")
        String description,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^https?://\\S+$", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.")
        String imageUrl,

        @Size(max = RecipeCreateRequest.MAX_TEXT_LENGTH, message = "단계별 팁은 2,000자 이하여야 합니다.")
        String tip
) {
    public RecipeStepRequest {
        description = description == null ? null : description.strip();
        imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.strip();
    }

    public RecipeStep toEntity(Recipe recipe, int stepNo) {
        return RecipeStep.builder()
                .recipe(recipe)
                .stepNo(stepNo)
                .description(description)
                .imageUrl(imageUrl)
                .tip(tip)
                .build();
    }
}
