package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
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
        // TODO: 프론트가 status로 바꾸면 삭제
        boolean imminent,
        // TODO: 프론트가 status로 바꾸면 삭제
        boolean expired,
        // 오늘 기준 소비기한까지 남은 일수 (오늘이면 0, 지났으면 음수)
        long dDay,
        FridgeItemStatus status,
        IngredientType ingredientType,
        // 기본 양념(재료의 is_basic_seasoning). 냉장고 목록의 기본 양념 탭과 개수 제외에 쓴다.
        boolean basicSeasoning,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // 구매 확정으로 들어온 줄의 상품 ID (fridge.product_id). 직접 등록한 줄과 예전 줄은 null
        Long productId
) {
    public FridgeItemResponse(Long id, Long ingredientId, String ingredientName, String baseUnit, String rawName,
                              BigDecimal quantity, StorageType storage, LocalDate expirationDate, FridgeSource source,
                              Long orderItemId, boolean imminent, boolean expired, long dDay, FridgeItemStatus status,
                              IngredientType ingredientType, boolean basicSeasoning, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
        this(id, ingredientId, ingredientName, baseUnit, rawName, quantity, storage, expirationDate, source, orderItemId,
                imminent, expired, dDay, status, ingredientType, basicSeasoning, createdAt, updatedAt, null);
    }

    // 한 요청 안의 응답들이 같은 기준일로 계산되도록 today를 호출하는 쪽에서 받는다.
    public static FridgeItemResponse from(Fridge fridge, LocalDate today) {
        Ingredient ingredient = fridge.getIngredient();
        long dDay = FridgeItemStatus.dDayOf(fridge.getExpirationDate(), today);
        FridgeItemStatus status = FridgeItemStatus.of(dDay);

        IngredientType ingredientType = IngredientType.NORMAL;
        if (ingredient != null) {
            if (ingredient.isCustom()) {
                ingredientType = IngredientType.CUSTOM;
            } else if (ingredient.isMealkit()) {
                ingredientType = IngredientType.MEALKIT;
            }
        }

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
                status == FridgeItemStatus.IMMINENT,
                status == FridgeItemStatus.EXPIRED,
                dDay,
                status,
                ingredientType,
                ingredient != null && ingredient.isBasicSeasoning(),
                fridge.getCreatedAt(),
                fridge.getUpdatedAt(),
                fridge.getProductId()
        );
    }
}
