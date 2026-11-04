package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 레시피 후보 검색 조건.
 * wantedTastes부터 excludeIngredientIds까지는 AI 상담 의도·조건 뽑기(ChatIntentFacade)가 채운다. 말하지 않은 조건은 빈 목록·null이다.
 *
 * @param selectedIngredientIds 선택 재료 ID: 내 냉장고에서 체크한 재료와 메시지에서 말한 재료를 합친 것 (없으면 빈 목록).
 *                              냉장고에 없는 재료여도 후보를 모으는 데 쓰고, 레시피에서는 부족한 재료로 나온다
 * @param moodCategories        원하는 레시피 분류 (recipes.category 값)
 * @param maxMinutes            최대 조리 시간(분). 조리 시간이 비어 있는 레시피는 거르지 않는다
 * @param useSelectedOnly       선택 재료를 꼭 쓰는 레시피만 원하는지 ("이 재료로", 칩 [이 재료로 추천받기])
 * @param selectedIngredientGroups 선택 재료별 같은 종류 재료 ID (예: 버섯 → 표고버섯·양송이버섯). 묶음 안의 재료를 하나라도 쓰면
 *                                 그 선택 재료를 쓴 것으로 센다. 후보 검색(RecipeCandidateFacade)이 채우며, 없으면 선택 재료 자신만이다
 */
public record RecipeQuery(
        Long memberId,
        List<Long> selectedIngredientIds,
        List<Member.Taste> wantedTastes,
        List<String> moodCategories,
        Integer maxMinutes,
        List<Long> excludeIngredientIds,
        boolean useSelectedOnly,
        Map<Long, Set<Long>> selectedIngredientGroups
) {
    public RecipeQuery {
        selectedIngredientGroups = selectedIngredientGroups == null ? Map.of() : Map.copyOf(selectedIngredientGroups);
        selectedIngredientIds = orEmpty(selectedIngredientIds);
        wantedTastes = orEmpty(wantedTastes);
        moodCategories = orEmpty(moodCategories);
        excludeIngredientIds = orEmpty(excludeIngredientIds);
    }

    public RecipeQuery(
            Long memberId,
            List<Long> selectedIngredientIds,
            List<Member.Taste> wantedTastes,
            List<String> moodCategories,
            Integer maxMinutes,
            List<Long> excludeIngredientIds,
            boolean useSelectedOnly
    ) {
        this(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes, excludeIngredientIds,
                useSelectedOnly, null);
    }

    // 선택 재료 묶음을 채운 검색 조건
    public RecipeQuery withSelectedIngredientGroups(Map<Long, Set<Long>> groups) {
        return new RecipeQuery(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes,
                excludeIngredientIds, useSelectedOnly, groups);
    }

    // 선택 재료 하나의 같은 종류 재료 ID (선택 재료 자신 포함)
    public Set<Long> selectedGroupOf(Long selectedId) {
        Set<Long> group = new LinkedHashSet<>();
        group.add(selectedId);
        group.addAll(selectedIngredientGroups.getOrDefault(selectedId, Set.of()));
        return group;
    }

    // AI 조건 없이 회원과 선택 재료만으로 검색할 때
    public static RecipeQuery of(Long memberId, List<Long> selectedIngredientIds) {
        return of(memberId, selectedIngredientIds, false);
    }

    public static RecipeQuery of(Long memberId, List<Long> selectedIngredientIds, boolean useSelectedOnly) {
        return new RecipeQuery(memberId, selectedIngredientIds, null, null, null, null, useSelectedOnly);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null).toList();
    }
}
