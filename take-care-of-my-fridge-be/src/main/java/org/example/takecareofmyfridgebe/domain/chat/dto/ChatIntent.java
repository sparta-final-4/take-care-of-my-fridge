package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 상담 메시지의 의도.
 * RECOMMEND: 메뉴·레시피 추천 요청, GENERAL: 그 밖의 질문(보관법·손질법·인사 등).
 * REFINE·RECIPE_DETAIL은 직전 추천이 있을 때만 쓴다 (의도 판단에 [직전 추천]을 넘긴 경우).
 * OFF_TOPIC은 답 생성 AI 없이 서버 안내로 답하고 하루 횟수를 돌려준다.
 */
public enum ChatIntent {
    RECOMMEND,
    GENERAL,
    // 직전 추천을 조건을 바꿔 다시 받고 싶은 말 ("좀 더 매운 걸로", "다른 거 없어?", "국물 말고 볶음으로")
    REFINE,
    // 추천한 레시피 하나를 자세히 묻는 말 ("두 번째 거 어떻게 만들어?", "깐풍기 시간 얼마나 걸려?")
    RECIPE_DETAIL,
    // 요리·식재료·냉장고·장보기·밀키트와 상관없는 일을 해 달라는 말 ("주식 추천해줘", "숙제 풀어줘", "파이썬 코드 짜줘")
    OFF_TOPIC
}
