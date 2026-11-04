package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;

/**
 * 레시피 후보 검색 조건.
 * wantedTastes 이하는 나중에 AI 조건 뽑기가 채울 칸으로, 지금은 빈 목록·null로 온다.
 *
 * @param selectedIngredientIds 내 냉장고에서 체크한 재료 ID (없으면 빈 목록)
 * @param moodCategories        원하는 레시피 분류 (recipes.category 값)
 * @param maxMinutes            최대 조리 시간(분). 조리 시간이 비어 있는 레시피는 거르지 않는다
 */
public record RecipeQuery(
        Long memberId,
        List<Long> selectedIngredientIds,
        List<Member.Taste> wantedTastes,
        List<String> moodCategories,
        Integer maxMinutes,
        List<Long> includeIngredientIds,
        List<Long> excludeIngredientIds
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
        return new RecipeQuery(memberId, selectedIngredientIds, null, null, null, null, null);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null).toList();
    }
}
