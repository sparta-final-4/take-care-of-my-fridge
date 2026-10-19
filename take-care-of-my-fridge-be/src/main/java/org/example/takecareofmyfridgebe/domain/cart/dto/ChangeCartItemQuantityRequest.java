package org.example.takecareofmyfridgebe.domain.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeCartItemQuantityRequest(
        @NotNull(message = "수량을 입력해 주세요.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
