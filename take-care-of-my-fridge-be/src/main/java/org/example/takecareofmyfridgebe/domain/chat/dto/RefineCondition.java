package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 다시 추천(REFINE)에서 빼라고 한 조건 종류. 의도 JSON의 removed 칸 값이다 ("매운 거 말고" → TASTE).
 * 빼라고 한 조건은 직전 값을 지우고 이번 말의 값만 쓴다.
 */
public enum RefineCondition {
    // 원하는 맛
    TASTE,
    // 요리 종류(분류)
    CATEGORY,
    // 조리 시간
    MAX_MINUTES,
    // 요리 이름 키워드
    KEYWORD,
    // 넣고 싶다던 재료(선택 재료)와 선택 재료만 쓰기
    INCLUDE,
    // 빼 달라던 재료
    EXCLUDE
}
