package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.List;

/**
 * 상담 추천 카드 SSE 이벤트(event: recommendations). 추천 설명 글보다 먼저 보낸다.
 *
 * @param recipes 추천 레시피 (최대 3개, 서버가 고른 순서)
 * @param relaxed 후보를 찾으려고 푼 조건 (조건 풀기 전이라 지금은 빈 목록)
 * @param empty   조건에 맞는 레시피 후보가 하나도 없는지
 */
public record ChatRecommendationsEventResponse(
        List<RecommendedRecipe> recipes,
        List<String> relaxed,
        boolean empty
) {

    public static ChatRecommendationsEventResponse of(List<RecommendedRecipe> recipes) {
        return new ChatRecommendationsEventResponse(List.copyOf(recipes), List.of(), recipes.isEmpty());
    }

    /**
     * 추천 카드 하나.
     *
     * @param cookTimeMin          조리 시간(분), 없으면 null
     * @param usedIngredients      쓰는 냉장고 재료 이름 (임박한 순)
     * @param imminentIngredients  그중 소비기한 임박(D-3 이내) 재료
     * @param matchedTastes        겹친 맛(한글). 사용자가 메시지로 맛을 말했으면 그 맛, 아니면 회원 선호 맛
     * @param missingIngredients   냉장고에 없는 재료 (기본 양념 제외, 구매 상품은 아직 없음)
     * @param lacksManyIngredients 재료를 많이 사야 하는지 (보유 비율 기준 미만)
     */
    public record RecommendedRecipe(
            Long recipeId,
            String name,
            String imageUrl,
            String category,
            Integer cookTimeMin,
            List<String> usedIngredients,
            List<ImminentIngredient> imminentIngredients,
            List<String> matchedTastes,
            List<MissingIngredient> missingIngredients,
            boolean lacksManyIngredients
    ) {
    }

    /**
     * @param dDay 오늘 기준 소비기한까지 남은 일수 (0이면 오늘까지)
     */
    public record ImminentIngredient(String name, long dDay) {
    }

    public record MissingIngredient(Long ingredientId, String name) {
    }
}
