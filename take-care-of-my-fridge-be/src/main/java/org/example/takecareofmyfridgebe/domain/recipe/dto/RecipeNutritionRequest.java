package org.example.takecareofmyfridgebe.domain.recipe.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// 1인분 기준 영양 정보. 모두 선택이다.
public record RecipeNutritionRequest(
        @PositiveOrZero(message = "중량은 0 이상이어야 합니다.")
        @Digits(integer = 6, fraction = 2, message = "중량은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal weightG,

        @PositiveOrZero(message = "열량은 0 이상이어야 합니다.")
        @Digits(integer = 6, fraction = 2, message = "열량은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal calories,

        @PositiveOrZero(message = "탄수화물은 0 이상이어야 합니다.")
        @Digits(integer = 6, fraction = 2, message = "탄수화물은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal carbsG,

        @PositiveOrZero(message = "단백질은 0 이상이어야 합니다.")
        @Digits(integer = 6, fraction = 2, message = "단백질은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal proteinG,

        @PositiveOrZero(message = "지방은 0 이상이어야 합니다.")
        @Digits(integer = 6, fraction = 2, message = "지방은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal fatG,

        @PositiveOrZero(message = "나트륨은 0 이상이어야 합니다.")
        @Digits(integer = 6, fraction = 2, message = "나트륨은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
        BigDecimal sodiumMg
) {
}
