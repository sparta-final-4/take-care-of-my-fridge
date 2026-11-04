package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * AI 메시지와 함께 저장할 레시피 추천 카드 하나 (보여 준 순서대로 넘긴다).
 *
 * @param recipeId    레시피 ID
 * @param description 카드 아래 AI 설명 (없으면 null)
 */
public record RecommendationToSave(
        Long recipeId,
        String description
) {
}
