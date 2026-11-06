package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * AI 메시지와 함께 저장할 추천 카드 하나 (보여 준 순서대로 넘긴다). 레시피 카드 다음에 밀키트 카드가 온다.
 * recipeId와 productId 중 하나만 있다.
 *
 * @param recipeId    레시피 ID (밀키트 카드면 null)
 * @param productId   밀키트 상품 ID (레시피 카드면 null)
 * @param description 카드 아래 설명 (없으면 null)
 */
public record RecommendationToSave(
        Long recipeId,
        Long productId,
        String description
) {

    // 레시피 카드
    public RecommendationToSave(Long recipeId, String description) {
        this(recipeId, null, description);
    }

    public static RecommendationToSave mealkit(Long productId, String description) {
        return new RecommendationToSave(null, productId, description);
    }
}
