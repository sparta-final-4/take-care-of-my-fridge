package org.example.takecareofmyfridgebe.domain.cooking.dto;

import java.math.BigDecimal;

public record IngredientDeductionResponse(
        Long ingredientId,
        BigDecimal deductedQuantity,
        BigDecimal remainingQuantity
) {
}
