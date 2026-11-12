package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 테스트용 냉장고 응답. 상품 ID(productId)가 없는 줄(직접 등록한 줄·예전 줄)을 만든다.
 */
public final class FridgeItemResponseFixture {

    private FridgeItemResponseFixture() {
    }

    public static FridgeItemResponse withoutProductId(Long id, Long ingredientId, String ingredientName, String baseUnit,
                                                      String rawName, BigDecimal quantity, StorageType storage,
                                                      LocalDate expirationDate, FridgeSource source, Long orderItemId,
                                                      boolean imminent, boolean expired, long dDay, FridgeItemStatus status,
                                                      IngredientType ingredientType, boolean basicSeasoning,
                                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new FridgeItemResponse(id, ingredientId, ingredientName, baseUnit, rawName, quantity, storage, expirationDate,
                source, orderItemId, imminent, expired, dDay, status, ingredientType, basicSeasoning, createdAt, updatedAt,
                null);
    }
}
