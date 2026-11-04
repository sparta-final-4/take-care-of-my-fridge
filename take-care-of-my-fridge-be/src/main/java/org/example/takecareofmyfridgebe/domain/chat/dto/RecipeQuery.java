package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;

/**
 * 레시피 후보 검색 조건.
 * wantedTastes부터 excludeIngredientIds까지는 AI 상담 의도·조건 뽑기(ChatIntentFacade)가 채운다. 말하지 않은 조건은 빈 목록·null이다.
 *
 * @param selectedIngredientIds 내 냉장고에서 체크한 재료 ID (없으면 빈 목록)
 * @param moodCategories        원하는 레시피 분류 (recipes.category 값)
 * @param maxMinutes            최대 조리 시간(분). 조리 시간이 비어 있는 레시피는 거르지 않는다
 * @param useSelectedOnly       선택 재료를 꼭 쓰는 레시피만 원하는지 ("이 재료로", 칩 [이 재료로 추천받기])
 */
public record RecipeQuery(
        Long memberId,
        List<Long> selectedIngredientIds,
        List<Member.Taste> wantedTastes,
        List<String> moodCategories,
        Integer maxMinutes,
        List<Long> includeIngredientIds,
        List<Long> excludeIngredientIds,
        boolean useSelectedOnly
) {
    public RecipeQuery {
        selectedIngredientIds = orEmpty(selectedIngredientIds);
        wantedTastes = orEmpty(wantedTastes);
        moodCategories = orEmpty(moodCategories);
        includeIngredientIds = orEmpty(includeIngredientIds);
        excludeIngredientIds = orEmpty(excludeIngredientIds);
    }

    // AI 조건 없이 회원과 선택 재료만으로 검색할 때
    public static RecipeQuery of(Long memberId, List<Long> selectedIngredientIds) {
        return of(memberId, selectedIngredientIds, false);
    }

    public static RecipeQuery of(Long memberId, List<Long> selectedIngredientIds, boolean useSelectedOnly) {
        return new RecipeQuery(memberId, selectedIngredientIds, null, null, null, null, null, useSelectedOnly);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null).toList();
    }
}
