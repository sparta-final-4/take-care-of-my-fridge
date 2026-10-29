package org.example.takecareofmyfridgebe.domain.fridge.dto;

/**
 * 냉장고 재료 수정 시 재매핑 여부를 판단하기 위한 비교용 정보
 *
 * @param rawName 저장된 재료 원문 이름
 * @param mealkit 매핑된 재료가 밀키트(ING_MEALKIT)인지 여부
 */
public record FridgeNameInfo(
        String rawName,
        boolean mealkit
) {
}
