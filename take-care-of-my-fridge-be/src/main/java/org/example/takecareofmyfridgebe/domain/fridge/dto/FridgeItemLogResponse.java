package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FridgeItemLogResponse(
        Long id,
        Long ingredientId,
        String ingredientName,
        String baseUnit,
        String rawName,
        // 냉장고에서 실제로 뺀 양
        BigDecimal quantity,
        FridgeItemAction action,
        // 레시피 요리 완료로 소진한 기록인지 여부 (cookingLogId가 있으면 true)
        boolean cookedByRecipe,
        LocalDateTime createdAt
) {
    public static FridgeItemLogResponse from(FridgeItemLog itemLog) {
        Ingredient ingredient = itemLog.getIngredient();
        return new FridgeItemLogResponse(
                itemLog.getId(),
                ingredient != null ? ingredient.getId() : null,
                ingredient != null ? ingredient.getName() : null,
                ingredient != null ? ingredient.getBaseUnit() : null,
                itemLog.getRawName(),
                itemLog.getQuantity(),
                itemLog.getAction(),
                itemLog.getCookingLogId() != null,
                itemLog.getCreatedAt()
        );
    }
}
