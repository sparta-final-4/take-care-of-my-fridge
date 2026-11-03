package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;

/**
 * 레시피 후보 하나. 정렬 단계별 값과 고른 근거를 함께 담는다.
 */
public record RecipeCandidate(
        Long recipeId,
        String name,
        Ranking ranking,
        Reason reason
) {

    /**
     * 정렬 단계별 값. 앞 단계가 같을 때만 다음 단계로 비교한다.
     *
     * @param requestMatches          1. 사용자 요청 일치 수 (포함 재료 + 원하는 맛 + 분류 일치)
     * @param selectedMatches         2. 선택 재료 겹친 수
     * @param tasteMatches            4. 선호 맛 겹친 수
     * @param imminentScore           3. 임박 점수 합 (D-0 12, D-1 10, D-2 8, D-3 6)
     * @param ownedCount              5. 가진 재료 종류 수 (보유 비율의 분자)
     * @param nonBasicIngredientCount 5. 기본 양념 뺀 재료 종류 수 (보유 비율의 분모)
     * @param missingCount            6. 없는 재료 수
     */
    public record Ranking(
            int requestMatches,
            int selectedMatches,
            int tasteMatches,
            int imminentScore,
            int ownedCount,
            int nonBasicIngredientCount,
            int missingCount
    ) {
        public double ownedRatio() {
            return nonBasicIngredientCount == 0 ? 0 : (double) ownedCount / nonBasicIngredientCount;
        }
    }

    /**
     * 고른 근거.
     *
     * @param usedFridgeIngredients 레시피에 쓰이는 냉장고 재료 이름 (임박한 순)
     * @param imminentIngredients   그중 소비기한 임박(D-3 이내) 재료
     * @param matchedTastes         회원 선호 맛과 겹친 레시피 맛
     * @param matchedRequests       맞은 사용자 요청 (예: "재료: 두부", "분류: 찌개", "맛: SPICY")
     * @param cookTimeUnknown       조리 시간 정보가 없어 시간 조건을 확인하지 못함
     * @param lacksManyIngredients  보유 비율이 기준 미만 (재료를 많이 사야 함). 선택 재료를 전부 써서 앞에 온 레시피도 표시한다
     * @param missingIngredients    냉장고에 없는 재료 (기본 양념 제외, 레시피에 나온 순)
     */
    public record Reason(
            List<String> usedFridgeIngredients,
            List<ImminentIngredient> imminentIngredients,
            List<Member.Taste> matchedTastes,
            List<String> matchedRequests,
            boolean cookTimeUnknown,
            boolean lacksManyIngredients,
            List<MissingIngredient> missingIngredients
    ) {
        public Reason withMissingIngredients(List<MissingIngredient> missing) {
            return new Reason(usedFridgeIngredients, imminentIngredients, matchedTastes, matchedRequests,
                    cookTimeUnknown, lacksManyIngredients, List.copyOf(missing));
        }
    }

    public record ImminentIngredient(String name, long dDay) {
    }

    /**
     * 부족한 재료. 재료 id로 판매 상품을 찾아 구매 링크를 만들 때 쓴다.
     */
    public record MissingIngredient(Long ingredientId, String name) {
    }
}
