package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.recommend.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 레시피 후보를 한 번 정렬(RecipeCandidateScorer.rank·scoreAll)하는 동안 바뀌지 않는 값.
 * 후보 레시피 정보(infos)와 정렬할 레시피 ID는 같은 값으로 여러 번 정렬할 때 달라질 수 있어 넣지 않고 따로 넘긴다.
 * of·withoutRequest로 만든다 (냉장고·재료 줄을 레시피 ID별로 한 번만 묶는다).
 *
 * @param query            검색 조건
 * @param nonBasicSelected 기본 양념을 뺀 선택 재료 ID (중복 제거)
 * @param fridgeById       재료 ID별 냉장고 재료
 * @param rowsByRecipe     레시피 ID별 재료 줄
 * @param preferred        회원 선호 맛
 * @param recipeTastes     레시피 ID별 맛 (비어 있으면 맛 단계는 모두 0)
 */
public record ScoringContext(
        RecipeQuery query,
        List<Long> nonBasicSelected,
        Map<Long, FridgeStock> fridgeById,
        Map<Long, List<RecipeCandidateIngredient>> rowsByRecipe,
        Set<Member.Taste> preferred,
        Map<Long, Set<Member.Taste>> recipeTastes
) {

    /**
     * @param fridge       prepareFridge로 준비한 냉장고 재료
     * @param rows         후보 레시피의 재료 줄
     * @param recipeTastes 레시피별 맛 (비어 있으면 맛 단계는 모두 0)
     */
    public static ScoringContext of(
            RecipeQuery query,
            List<FridgeStock> fridge,
            Collection<Member.Taste> memberTastes,
            List<RecipeCandidateIngredient> rows,
            Map<Long, Set<Member.Taste>> recipeTastes
    ) {
        Map<Long, FridgeStock> fridgeById = fridge.stream()
                .collect(Collectors.toMap(FridgeStock::ingredientId, stock -> stock, (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<RecipeCandidateIngredient>> rowsByRecipe = rows.stream()
                .collect(Collectors.groupingBy(RecipeCandidateIngredient::recipeId, LinkedHashMap::new, Collectors.toList()));
        return new ScoringContext(query, nonBasicSelectedIds(query, rows), fridgeById, rowsByRecipe, toTasteSet(memberTastes),
                recipeTastes);
    }

    // 요청 조건·선택 재료 없이 계산할 때 (저장된 추천 카드를 지금 냉장고 기준으로 다시 보여 줄 때)
    public static ScoringContext withoutRequest(
            Long memberId,
            List<FridgeStock> fridge,
            Collection<Member.Taste> memberTastes,
            List<RecipeCandidateIngredient> rows,
            Map<Long, Set<Member.Taste>> recipeTastes
    ) {
        return of(RecipeQuery.of(memberId, List.of()), fridge, memberTastes, rows, recipeTastes);
    }

    List<RecipeCandidateIngredient> rowsOf(Long recipeId) {
        return rowsByRecipe.getOrDefault(recipeId, List.of());
    }

    Set<Member.Taste> tastesOf(Long recipeId) {
        return recipeTastes.getOrDefault(recipeId, Set.of());
    }

    // 기본 양념을 뺀 선택 재료 (중복 제거). 레시피 재료 줄에서 기본 양념으로 확인된 재료만 뺀다
    // (어느 레시피에도 없는 재료는 기본 양념이 아닌 것으로 센다. 그런 재료를 전부 쓰는 레시피는 없으므로 예외도 생기지 않는다)
    private static List<Long> nonBasicSelectedIds(RecipeQuery query, List<RecipeCandidateIngredient> rows) {
        Set<Long> basicIds = rows.stream()
                .filter(RecipeCandidateIngredient::basicSeasoning)
                .map(RecipeCandidateIngredient::ingredientId)
                .collect(Collectors.toSet());
        return query.selectedIngredientIds().stream()
                .distinct()
                .filter(id -> !basicIds.contains(id))
                .toList();
    }

    private static Set<Member.Taste> toTasteSet(Collection<Member.Taste> tastes) {
        Set<Member.Taste> set = EnumSet.noneOf(Member.Taste.class);
        if (tastes != null) {
            tastes.stream().filter(taste -> taste != null).forEach(set::add);
        }
        return set;
    }
}
