package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 레시피 후보 검색 조건.
 * wantedTastes부터 preferenceText까지는 AI 상담 의도·조건 뽑기(ChatIntentFacade)가 채운다. 말하지 않은 조건은 빈 목록·null이다.
 *
 * @param selectedIngredientIds    선택 재료 ID: 내 냉장고에서 체크한 재료와 메시지에서 말한 재료를 합친 것 (없으면 빈 목록).
 *                                 냉장고에 없는 재료여도 후보를 모으는 데 쓰고, 레시피에서는 부족한 재료로 나온다
 * @param moodCategories           원하는 레시피 분류 (recipes.category 값)
 * @param maxMinutes               최대 조리 시간(분). 조리 시간이 비어 있는 레시피는 거르지 않는다
 * @param useSelectedOnly          선택 재료를 꼭 쓰는 레시피만 원하는지 ("이 재료로", 칩 [이 재료로 추천받기])
 * @param keywords                 레시피 이름에 나올 만한 요리 이름 (예: 냉면, 막국수). 레시피 검색(ES)으로 후보를 더 모은다
 * @param keywordText              사용자가 말한 요리 이름 (예: "냉면"). 레시피 이름에 통째로 있는지 볼 때와 카드 이유에 쓴다
 * @param preferenceText           사용자가 원한 특징·요리 표현 그대로 (예: "차가운 요리"). 고른 레시피가 요청에 하나도 안 맞으면 안내에 쓴다
 * @param selectedIngredientGroups 선택 재료별 같은 종류 재료 ID (예: 버섯 → 표고버섯·양송이버섯). 묶음 안의 재료를 하나라도 쓰면
 *                                 그 선택 재료를 쓴 것으로 센다. 후보 검색(RecipeCandidateFacade)이 채우며, 없으면 선택 재료 자신만이다
 * @param alreadyRecommendedIds    이 상담에서 이미 추천한 레시피 ID. 정렬에서 요청 바로 뒤에 "추천하지 않은 것 먼저"로 쓴다
 * @param keywordMatches           키워드 검색으로 찾은 레시피 ID별 맞은 방식(이름·늘린 이름·재료·쪼개짐·벡터). 후보 검색(RecipeCandidateFacade)이 채운다
 * @param avoidTastes              피할 맛 ("매운 거 말고" → 매운맛). 이 맛인 레시피는 후보에서 빼고(3개가 안 되면 뒤에 채움), 원하는 맛·회원 선호 맛보다 앞선다
 */
public record RecipeQuery(
        Long memberId,
        List<Long> selectedIngredientIds,
        List<Member.Taste> wantedTastes,
        List<String> moodCategories,
        Integer maxMinutes,
        List<Long> excludeIngredientIds,
        boolean useSelectedOnly,
        List<String> keywords,
        String keywordText,
        String preferenceText,
        Map<Long, Set<Long>> selectedIngredientGroups,
        Map<Long, KeywordMatch> keywordMatches,
        Set<Long> alreadyRecommendedIds,
        List<Member.Taste> avoidTastes
) {
    public RecipeQuery {
        selectedIngredientIds = orEmpty(selectedIngredientIds);
        wantedTastes = orEmpty(wantedTastes);
        moodCategories = orEmpty(moodCategories);
        excludeIngredientIds = orEmpty(excludeIngredientIds);
        keywords = orEmpty(keywords);
        keywordText = keywordText == null || keywordText.isBlank() ? null : keywordText.strip();
        preferenceText = preferenceText == null || preferenceText.isBlank() ? null : preferenceText.strip();
        selectedIngredientGroups = selectedIngredientGroups == null ? Map.of() : Map.copyOf(selectedIngredientGroups);
        keywordMatches = keywordMatches == null ? Map.of() : Map.copyOf(keywordMatches);
        alreadyRecommendedIds = alreadyRecommendedIds == null ? Set.of() : Set.copyOf(alreadyRecommendedIds);
        avoidTastes = orEmpty(avoidTastes);
    }

    public RecipeQuery(
            Long memberId,
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
        this(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes, excludeIngredientIds,
                useSelectedOnly, keywords, keywordText, preferenceText, null, null, null, null);
    }

    public RecipeQuery(
            Long memberId,
            List<Long> selectedIngredientIds,
            List<Member.Taste> wantedTastes,
            List<String> moodCategories,
            Integer maxMinutes,
            List<Long> excludeIngredientIds,
            boolean useSelectedOnly,
            List<String> keywords,
            String keywordText
    ) {
        this(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes, excludeIngredientIds,
                useSelectedOnly, keywords, keywordText, null);
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
                useSelectedOnly, null, null);
    }

    // 선택 재료 묶음을 채운 검색 조건
    public RecipeQuery withSelectedIngredientGroups(Map<Long, Set<Long>> groups) {
        return new RecipeQuery(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes,
                excludeIngredientIds, useSelectedOnly, keywords, keywordText, preferenceText, groups, keywordMatches,
                alreadyRecommendedIds, avoidTastes);
    }

    // 선택 재료에서 주어진 재료(기본 양념 등)를 뺀 검색 조건. 그 재료의 같은 종류 묶음도 뺀다
    public RecipeQuery withoutSelectedIngredients(Set<Long> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return this;
        }
        List<Long> selected = selectedIngredientIds.stream().filter(id -> !ingredientIds.contains(id)).toList();
        Map<Long, Set<Long>> groups = new HashMap<>(selectedIngredientGroups);
        groups.keySet().removeAll(ingredientIds);
        return new RecipeQuery(memberId, selected, wantedTastes, moodCategories, maxMinutes,
                excludeIngredientIds, useSelectedOnly, keywords, keywordText, preferenceText, groups, keywordMatches,
                alreadyRecommendedIds, avoidTastes);
    }

    // 키워드 검색 결과를 채운 검색 조건
    public RecipeQuery withKeywordMatches(Map<Long, KeywordMatch> matches) {
        return new RecipeQuery(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes,
                excludeIngredientIds, useSelectedOnly, keywords, keywordText, preferenceText, selectedIngredientGroups, matches,
                alreadyRecommendedIds, avoidTastes);
    }

    // 이 상담에서 이미 추천한 레시피를 채운 검색 조건
    public RecipeQuery withAlreadyRecommended(Set<Long> recipeIds) {
        return new RecipeQuery(memberId, selectedIngredientIds, wantedTastes, moodCategories, maxMinutes,
                excludeIngredientIds, useSelectedOnly, keywords, keywordText, preferenceText, selectedIngredientGroups,
                keywordMatches, recipeIds, avoidTastes);
    }

    // 피할 맛을 채운 검색 조건. 원하는 맛과 겹치면 피할 맛이 이긴다 (원하는 맛에서 뺀다)
    public RecipeQuery withAvoidTastes(List<Member.Taste> tastes) {
        List<Member.Taste> avoid = orEmpty(tastes).stream().distinct().toList();
        List<Member.Taste> wanted = wantedTastes.stream().filter(taste -> !avoid.contains(taste)).toList();
        return new RecipeQuery(memberId, selectedIngredientIds, wanted, moodCategories, maxMinutes,
                excludeIngredientIds, useSelectedOnly, keywords, keywordText, preferenceText, selectedIngredientGroups,
                keywordMatches, alreadyRecommendedIds, avoid);
    }

    // 말한 요리를 찾지 못했는지.
    // 요리 이름(keywordText)을 말했으면 그 이름이 들어간 레시피가 하나도 없을 때 (비슷한 탕수육을 찾았어도 김피탕이 없으면 못 찾음).
    // 이름 없이 키워드만 있으면 이름·재료로 맞은 레시피가 하나도 없을 때 (쪼개짐·벡터로만 찾은 것은 맞은 것으로 치지 않는다)
    public boolean keywordsNotFound() {
        if (keywords.isEmpty()) {
            return false;
        }
        if (keywordText != null) {
            return keywordMatches.values().stream().noneMatch(match -> match.type() == KeywordMatch.Type.NAME);
        }
        return keywordMatches.values().stream().noneMatch(KeywordMatch::strong);
    }

    // 사용자 요청 조건(요리 이름 키워드·분류·요리 이름)이 있는지. 있으면 회원 선호 맛은 말하지 않는다
    public boolean hasRequest() {
        return !keywords.isEmpty() || !moodCategories.isEmpty() || keywordText != null;
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
