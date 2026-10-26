package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;

import java.math.BigDecimal;

// 레시피 등록, 수정에서 함께 쓰는 재료 항목. 순서(seq)는 목록 위치로 정한다.
public record RecipeIngredientRequest(
        @NotNull(message = "연결할 재료를 선택해 주세요.")
        Long ingredientId,

        @NotBlank(message = "재료명을 입력해 주세요.")
        @Size(max = 100, message = "재료명은 100자 이하여야 합니다.")
        String displayName,

        @Size(max = 100, message = "용량은 100자 이하여야 합니다.")
        String amount,

        @Positive(message = "수량은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "수량은 정수 8자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal quantity,

        @Size(max = 20, message = "재료 역할은 20자 이하여야 합니다.")
        String role,

        @Size(max = 50, message = "재료 그룹명은 50자 이하여야 합니다.")
        String groupName
) {
    public RecipeIngredientRequest {
        displayName = displayName == null ? null : displayName.strip();
    }

    public RecipeIngredient toEntity(Recipe recipe, int seq) {
        return RecipeIngredient.builder()
                .recipe(recipe)
                .ingredientId(ingredientId)
                .seq(seq)
                .displayName(displayName)
                .amount(amount)
                .quantity(quantity)
                .role(role)
                .groupName(groupName)
                .build();
    }
}
