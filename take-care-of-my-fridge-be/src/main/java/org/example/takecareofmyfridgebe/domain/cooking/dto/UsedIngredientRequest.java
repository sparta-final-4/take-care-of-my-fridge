package org.example.takecareofmyfridgebe.domain.cooking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UsedIngredientRequest(
        @NotNull(message = "재료 ID는 필수입니다.")
        Long ingredientId,

        @Positive(message = "수량은 0보다 커야 합니다.")
        BigDecimal quantity
) {
}
