package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatRecommendation;

/**
 * 저장된 추천 카드 하나 (다시 불러올 때 쓴다). 레시피 카드면 recipeId, 밀키트 카드면 productId가 있다.
 *
 * @param messageId    카드가 붙은 AI 메시지 ID
 * @param productId    밀키트 상품 ID (레시피 카드면 null)
 * @param displayOrder 보여 줄 순서 (0부터, 레시피 카드 다음 밀키트)
 * @param description  카드 아래 설명 (없으면 null)
 */
public record SavedRecommendation(
        Long messageId,
        Long recipeId,
        Long productId,
        int displayOrder,
        String description
) {

    // 레시피 카드
    public SavedRecommendation(Long messageId, Long recipeId, int displayOrder, String description) {
        this(messageId, recipeId, null, displayOrder, description);
    }

    public static SavedRecommendation from(ChatRecommendation recommendation) {
        return new SavedRecommendation(
                recommendation.getMessage().getId(),
                recommendation.getRecipeId(),
                recommendation.getProductId(),
                recommendation.getDisplayOrder(),
                recommendation.getDescription()
        );
    }

    public boolean isMealkit() {
        return productId != null;
    }
}
