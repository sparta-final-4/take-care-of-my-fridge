package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatRecommendation;

/**
 * 저장된 레시피 추천 카드 하나 (다시 불러올 때 쓴다).
 *
 * @param messageId    카드가 붙은 AI 메시지 ID
 * @param displayOrder 보여 줄 순서 (0부터)
 * @param description  카드 아래 AI 설명 (없으면 null)
 */
public record SavedRecommendation(
        Long messageId,
        Long recipeId,
        int displayOrder,
        String description
) {
    public static SavedRecommendation from(ChatRecommendation recommendation) {
        return new SavedRecommendation(
                recommendation.getMessage().getId(),
                recommendation.getRecipeId(),
                recommendation.getDisplayOrder(),
                recommendation.getDescription()
        );
    }
}
