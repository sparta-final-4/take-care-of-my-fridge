package org.example.takecareofmyfridgebe.domain.search.dto;

import java.util.List;

/**
 * 통합검색 결과와 내 냉장고 비교
 *
 * @param recipes       레시피별 보유 재료 수. 요청 순서대로
 * @param keywordItems  검색어가 든 내 냉장고 재료 이름(입력한 이름). 없으면 빈 목록
 */
public record SearchFridgeMatchResponse(
        List<RecipeMatch> recipes,
        List<String> keywordItems
) {

    /**
     * @param recipeId        레시피 ID
     * @param ownedCount      그중 냉장고에 있는 재료 수
     * @param ingredientCount 비교한 재료 수 (기본 양념 등 제외)
     */
    public record RecipeMatch(
            Long recipeId,
            int ownedCount,
            int ingredientCount
    ) {
    }
}
