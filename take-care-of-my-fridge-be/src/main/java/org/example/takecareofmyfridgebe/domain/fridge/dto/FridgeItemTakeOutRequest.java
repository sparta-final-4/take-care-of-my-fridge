package org.example.takecareofmyfridgebe.domain.fridge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;

import java.math.BigDecimal;

public record FridgeItemTakeOutRequest(
        @NotNull(message = "먹었는지 버렸는지 선택해 주세요.")
        FridgeItemAction action,

        // null이면 남은 양 전부를 뺀다. 남은 양보다 크게 보내도 전부 뺀 것으로 처리한다.
        @Positive(message = "수량은 0보다 커야 합니다.")
        BigDecimal quantity
) {
}
