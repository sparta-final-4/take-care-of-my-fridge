package org.example.takecareofmyfridgebe.domain.fridge.dto;

/**
 * 다른 도메인(search)에 넘기는 냉장고 재료 정보
 *
 * @param ingredientId   매핑된 표준 재료 ID
 * @param ingredientName 표준 재료 이름
 * @param rawName        사용자가 입력한 재료 이름
 */
public record FridgeIngredientInfo(
        Long ingredientId,
        String ingredientName,
        String rawName
) {
}
