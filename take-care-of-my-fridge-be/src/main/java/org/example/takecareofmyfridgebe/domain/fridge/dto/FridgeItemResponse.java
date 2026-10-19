package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FridgeItemResponse(
        Long id,
        Long ingredientId,
        String ingredientName,
        String baseUnit,
        String rawName,
        BigDecimal quantity,
        StorageType storage,
        LocalDate expirationDate,
        FridgeSource source,
        Long orderItemId,
        boolean imminent,
        boolean expired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FridgeItemResponse from(Fridge fridge) {
        LocalDate today = LocalDate.now();
        Ingredient ingredient = fridge.getIngredient();
        return new FridgeItemResponse(
                fridge.getId(),
                ingredient != null ? ingredient.getId() : null,
                ingredient != null ? ingredient.getName() : null,
                ingredient != null ? ingredient.getBaseUnit() : null,
                fridge.getRawName(),
                fridge.getQuantity(),
                fridge.getStorage(),
                fridge.getExpirationDate(),
                fridge.getSource(),
                fridge.getOrderItemId(),
                fridge.isImminent(today),
                fridge.isExpired(today),
                fridge.getCreatedAt(),
                fridge.getUpdatedAt()
        );
    }
}
