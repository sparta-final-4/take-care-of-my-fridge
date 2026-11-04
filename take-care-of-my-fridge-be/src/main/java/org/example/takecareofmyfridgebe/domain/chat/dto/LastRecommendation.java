package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;

/**
 * 상담의 직전 추천: 그때 쓴 추천 조건과 보여 준 카드 레시피 ID (보여 준 순서).
 * Redis(chat:context:{sessionId})에 두고, "좀 더 매운 걸로"(다시 추천)와 "두 번째 거"(레시피 자세히)를 알아들을 때 쓴다.
 *
 * @param query     직전 추천 조건 (선택 재료 묶음·키워드 검색 결과·이미 추천한 레시피처럼 검색 중에 채우는 값은 빼고 둔다)
 * @param recipeIds 직전 추천 카드 레시피 ID (보여 준 순서)
 */
public record LastRecommendation(
        QuerySnapshot query,
        List<Long> recipeIds
) {

    public LastRecommendation {
        recipeIds = recipeIds == null ? List.of() : List.copyOf(recipeIds);
    }

    public static LastRecommendation of(RecipeQuery query, List<Long> recipeIds) {
        return new LastRecommendation(QuerySnapshot.from(query), recipeIds);
    }

    /**
     * Redis에 둘 추천 조건. RecipeQuery 중 사용자가 정한 값만 담는다.
     */
    public record QuerySnapshot(
            List<Long> selectedIngredientIds,
            List<Member.Taste> wantedTastes,
            List<String> moodCategories,
            Integer maxMinutes,
            List<Long> excludeIngredientIds,
            boolean useSelectedOnly,
            List<String> keywords,
            String keywordText,
            String preferenceText
    ) {

        public static QuerySnapshot from(RecipeQuery query) {
            return new QuerySnapshot(query.selectedIngredientIds(), query.wantedTastes(), query.moodCategories(),
                    query.maxMinutes(), query.excludeIngredientIds(), query.useSelectedOnly(), query.keywords(),
                    query.keywordText(), query.preferenceText());
        }

        public RecipeQuery toRecipeQuery(Long memberId) {
            return new RecipeQuery(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes,
                    excludeIngredientIds, useSelectedOnly, keywords, keywordText, preferenceText);
        }
    }
}
