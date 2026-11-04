package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 추천 카드 설명 조각 SSE 이벤트(event: recommendation_text). 소개 글은 message 이벤트로 따로 온다.
 *
 * @param index 카드 순서 (0부터, recommendations 이벤트의 recipes 순서)
 * @param text  그 카드 설명의 이어지는 글
 */
public record ChatRecommendationTextEventResponse(
        int index,
        String text
) {
}
