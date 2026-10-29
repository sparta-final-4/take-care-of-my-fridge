package org.example.takecareofmyfridgebe.domain.ingredient.entity;

/**
 * 냉장고 재료의 사전 매핑 및 특수 종류 구분
 */
public enum IngredientType {
    /**
     * 표준 재료 사전에 등록된 일반 재료 (레시피 추천 및 요리 차감 대상)
     */
    NORMAL,

    /**
     * 표준 재료 사전에 없는 사용자 직접 입력 재료 (ING_CUSTOM)
     */
    CUSTOM,

    /**
     * 밀키트 상품 구매 확정으로 등록된 재료 (ING_MEALKIT, 요리 차감 제외)
     */
    MEALKIT
}
