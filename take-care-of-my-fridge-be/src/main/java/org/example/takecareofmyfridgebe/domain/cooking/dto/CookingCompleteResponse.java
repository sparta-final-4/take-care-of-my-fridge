package org.example.takecareofmyfridgebe.domain.cooking.dto;

import java.util.List;

public record CookingCompleteResponse(
        Long cookingLogId,
        List<IngredientDeductionResponse> deductions
) {
}
