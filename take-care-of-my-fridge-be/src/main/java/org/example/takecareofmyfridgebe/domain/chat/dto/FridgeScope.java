package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 냉장고 보기(FRIDGE)에서 보여 줄 범위. 재료 이름을 말했으면 범위와 상관없이 그 재료만 본다.
 */
public enum FridgeScope {
    // 기본 양념을 뺀 전체 ("냉장고에 뭐 있어")
    ALL,
    // 밀키트 줄만 ("밀키트 뭐 남았어")
    MEALKIT,
    // 소비기한이 D-3 이내이거나 지난 줄 ("곧 버려야 할 거 있어?", "유통기한 지난 거 있어?")
    IMMINENT
}
