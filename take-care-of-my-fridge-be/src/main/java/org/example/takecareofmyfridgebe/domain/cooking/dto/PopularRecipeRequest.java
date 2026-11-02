package org.example.takecareofmyfridgebe.domain.cooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PopularRecipeRequest(
        // 비우면 6건
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 20, message = "size는 20 이하여야 합니다.") Integer size
) {
    public PopularRecipeRequest {
        size = size == null ? 6 : size;
    }
}
