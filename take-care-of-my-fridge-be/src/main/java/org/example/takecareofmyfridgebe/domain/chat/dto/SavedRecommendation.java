package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatRecommendation;

/**
 * 저장된 추천 카드 하나 (다시 불러올 때 쓴다). 레시피 카드면 recipeId, 상품 카드(밀키트·재료 상품)면 productId가 있다.
 *
 * @param messageId    카드가 붙은 AI 메시지 ID
 * @param productId    밀키트·재료 상품 ID (레시피 카드면 null)
 * @param displayOrder 보여 줄 순서 (0부터, 레시피 카드 다음 밀키트, 그다음 재료 상품)
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

    // 상품 카드(밀키트·재료 상품). 밀키트인지 재료 상품인지는 상품 종류(productType)로 다시 만들 때 나눈다
    public boolean isProduct() {
        return productId != null;
    }
}
