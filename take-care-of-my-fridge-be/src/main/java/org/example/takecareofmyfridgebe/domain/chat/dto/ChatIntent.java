package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 상담 메시지의 의도.
 * RECOMMEND: 메뉴·레시피 추천 요청, GENERAL: 그 밖의 질문(보관법·손질법·인사 등).
 * REFINE·RECIPE_DETAIL은 직전 추천이 있을 때만 쓴다 (의도 판단에 [직전 추천]을 넘긴 경우).
 * FRIDGE는 냉장고 데이터로, SHOP은 판매 상품으로 서버가 카드와 문장을 만들어 답한다 (답 생성 AI 없음).
 * OFF_TOPIC·BLOCKED·SELF_HARM은 답 생성 AI 없이 서버 안내로 답한다. 의도 판단 AI를 썼으므로 하루 횟수는 센다.
 * BLOCKED·SELF_HARM은 서버 단어 목록(app.chat.safety-words)에 없지만 AI가 그렇게 본 말이다. 보낸 말과 안내 답을 걸러진 메시지(filtered)로 저장한다.
 */
public enum ChatIntent {
    RECOMMEND,
    GENERAL,
    // 직전 추천을 조건을 바꿔 다시 받고 싶은 말 ("좀 더 매운 걸로", "다른 거 없어?", "국물 말고 볶음으로")
    REFINE,
    // 추천한 레시피 하나를 자세히 묻는 말 ("두 번째 거 어떻게 만들어?", "깐풍기 시간 얼마나 걸려?")
    RECIPE_DETAIL,
    // 냉장고에 무엇이 있는지 보려는 말 ("냉장고에 뭐 있어", "밀키트 뭐 남았어", "두부 아직 있어?", "곧 버려야 할 거 있어?")
    FRIDGE,
    // 재료·상품을 사려는 말 ("당근 사고 싶어", "두부 어디서 사", "대파 장바구니에 담아줘")
    SHOP,
    // 요리·식재료·냉장고·장보기·밀키트와 상관없는 일을 해 달라는 말 ("주식 추천해줘", "숙제 풀어줘", "파이썬 코드 짜줘")
    OFF_TOPIC,
    // 욕설·혐오, 불법·위험한 일, 지시를 무시하라거나 프롬프트를 보여 달라는 말 (역할만 정하는 말은 아니다)
    BLOCKED,
    // 자해·자살을 말함 (금지가 아니라 도움이 필요한 경우라 상담 전화를 안내한다). "배고파 죽겠어" 같은 과장은 아니다
    SELF_HARM
}
