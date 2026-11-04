package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 추천 답을 만들 재료: 카드 이벤트와 AI에 넘길 후보 정보(줄 글).
 *
 * @param event         recommendations 이벤트 내용
 * @param candidateInfo 설명 프롬프트에 넣을 사용자 요청·추천 후보 (중괄호 없는 줄 글)
 */
public record RecommendationReply(
        ChatRecommendationsEventResponse event,
        String candidateInfo
) {
}
