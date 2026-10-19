package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;

import java.math.BigDecimal;

public record FridgeItemTakeOutResponse(
        Long fridgeId,
        // 남은 양이 없어 냉장고에서 삭제되었는지 여부
        boolean removed,
        // 처리 후 남은 양 (삭제되었으면 0)
        BigDecimal remainingQuantity
) {
    public static FridgeItemTakeOutResponse from(Fridge fridge) {
        return new FridgeItemTakeOutResponse(fridge.getId(), fridge.isEmpty(), fridge.getQuantity());
    }
}
