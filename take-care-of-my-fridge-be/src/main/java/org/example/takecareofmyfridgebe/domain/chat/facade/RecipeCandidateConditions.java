package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.Condition;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 상담 추천 조건(RecipeQuery)을 레시피 후보 검색(ES) 조건으로 바꾼다. 스프링에 의존하지 않는 순수 클래스.
 * <ul>
 *   <li>선택 재료는 같은 종류 묶음까지 펼친다 (버섯 → 버섯·표고버섯·양송이버섯)</li>
 *   <li>기본 양념인 선택 재료는 넣지 않는다 (선택 재료만 쓰기에서 양념 하나로 통과하지 않게). 호출하는 쪽이 미리 뺀다</li>
 *   <li>임박 재료는 냉장고 재료 중 D-3 이내 (FridgeItemStatus와 같은 기준)</li>
 *   <li>직전 카드는 넘기지 않는다. 빼서 3개가 안 되면 다시 넣어야 해서 호출하는 쪽(ChatRecommendationFacade)이 뺀다</li>
 * </ul>
 */
public final class RecipeCandidateConditions {

    private RecipeCandidateConditions() {
    }

    /**
     * @param query              기본 양념인 선택 재료를 뺀 추천 조건 (선택 재료 묶음이 채워진 것)
     * @param fridge             prepareFridge로 준비한 냉장고 재료 (기본 양념·기한 지난 재료 없음)
     * @param excludedCategories 후보에서 뺄 레시피 분류
     */
    public static Condition from(RecipeQuery query, List<FridgeStock> fridge, Collection<String> excludedCategories) {
        Set<Long> selected = new LinkedHashSet<>();
        query.selectedIngredientIds().forEach(selectedId -> selected.addAll(query.selectedGroupOf(selectedId)));
        List<Long> fridgeIds = fridge.stream().map(FridgeStock::ingredientId).distinct().toList();
        List<Long> imminentIds = fridge.stream()
                .filter(stock -> stock.dDay() <= FridgeItemStatus.IMMINENT_DAYS)
                .map(FridgeStock::ingredientId)
                .distinct()
                .toList();
        return new Condition(
                fridgeIds,
                imminentIds,
                List.copyOf(selected),
                query.useSelectedOnly(),
                query.moodCategories(),
                query.keywords(),
                query.wantedTastes(),
                query.avoidTastes(),
                query.maxMinutes(),
                query.excludeIngredientIds(),
                List.copyOf(excludedCategories),
                Set.of());
    }

    // 분류나 키워드를 말했는지. 말했으면 요청 조건만 넣은 검색을 한 번 더 한다
    public static boolean hasRequest(Condition condition) {
        return !condition.categories().isEmpty() || !condition.keywords().isEmpty();
    }

    /**
     * 분류·키워드만으로 점수를 내는 조건. 냉장고 재료가 많이 겹치는 레시피에 밀려 분류만 맞은 레시피가 100건 밖으로 빠지지 않게 한다.
     * 거르는 조건(피할 맛·최대 시간·뺄 재료·뺄 분류)은 그대로 둔다. 선택 재료만 쓰기면 선택 재료도 남긴다 (꼭 들어 있어야 하므로).
     */
    public static Condition requestOnly(Condition condition) {
        return new Condition(
                List.of(),
                List.of(),
                condition.selectedOnly() ? condition.selectedIngredientIds() : List.of(),
                condition.selectedOnly(),
                condition.categories(),
                condition.keywords(),
                List.of(),
                condition.avoidTastes(),
                condition.maxMinutes(),
                condition.excludeIngredientIds(),
                condition.excludeCategories(),
                condition.excludeRecipeIds());
    }
}
