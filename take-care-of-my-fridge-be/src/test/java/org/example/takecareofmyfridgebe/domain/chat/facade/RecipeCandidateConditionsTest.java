package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateScorer.FridgeStock;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCandidateConditionsTest {

    private static final Long MEMBER_ID = 1L;
    private static final List<String> EXCLUDED = List.of("양념", "음료");

    @Test
    @DisplayName("선택 재료는 같은 종류 묶음까지 펼치고, 직전 카드는 넘기지 않는다")
    void expandsSelectedGroups() {
        RecipeQuery query = RecipeQuery.of(MEMBER_ID, List.of(45L, 1L), true)
                .withSelectedIngredientGroups(Map.of(45L, Set.of(161L, 162L)))
                .withAlreadyRecommended(Set.of(99L));

        Condition condition = RecipeCandidateConditions.from(query, List.of(), EXCLUDED);

        assertThat(condition.selectedIngredientIds()).containsExactlyInAnyOrder(45L, 161L, 162L, 1L);
        assertThat(condition.selectedOnly()).isTrue();
        assertThat(condition.excludeRecipeIds()).isEmpty();
        assertThat(condition.excludeCategories()).containsExactly("양념", "음료");
    }

    @Test
    @DisplayName("냉장고 재료는 모두 넘기고 임박 재료는 D-3 이내만 넘긴다")
    void passesImminentWithinThreeDays() {
        List<FridgeStock> fridge = List.of(
                new FridgeStock(1L, "두부", 0),
                new FridgeStock(2L, "양파", 3),
                new FridgeStock(3L, "계란", 4));

        Condition condition = RecipeCandidateConditions.from(RecipeQuery.of(MEMBER_ID, List.of()), fridge, EXCLUDED);

        assertThat(condition.fridgeIngredientIds()).containsExactly(1L, 2L, 3L);
        assertThat(condition.imminentIngredientIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("분류·키워드·맛·시간·뺄 재료·피할 맛은 그대로 넘긴다")
    void passesRequestAndFilterConditions() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SWEET), List.of("디저트"), 30, List.of(7L),
                false, List.of("약과"), "약과").withAvoidTastes(List.of(Taste.SPICY));

        Condition condition = RecipeCandidateConditions.from(query, List.of(), EXCLUDED);

        assertThat(condition.categories()).containsExactly("디저트");
        assertThat(condition.keywords()).containsExactly("약과");
        assertThat(condition.wantedTastes()).containsExactly(Taste.SWEET);
        assertThat(condition.avoidTastes()).containsExactly(Taste.SPICY);
        assertThat(condition.maxMinutes()).isEqualTo(30);
        assertThat(condition.excludeIngredientIds()).containsExactly(7L);
        assertThat(RecipeCandidateConditions.hasRequest(condition)).isTrue();
    }

    @Test
    @DisplayName("요청만 검색 조건은 냉장고·임박·원하는 맛 점수를 비우고 거르는 조건은 남긴다")
    void keepsOnlyRequestScores_whenRequestOnly() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(1L), List.of(Taste.SWEET), List.of("디저트"), 30, List.of(7L),
                false).withAvoidTastes(List.of(Taste.SPICY));
        Condition condition = RecipeCandidateConditions.from(query, List.of(new FridgeStock(2L, "양파", 0)), EXCLUDED);

        Condition requestOnly = RecipeCandidateConditions.requestOnly(condition);

        assertThat(requestOnly.fridgeIngredientIds()).isEmpty();
        assertThat(requestOnly.imminentIngredientIds()).isEmpty();
        assertThat(requestOnly.selectedIngredientIds()).isEmpty();
        assertThat(requestOnly.wantedTastes()).isEmpty();
        assertThat(requestOnly.categories()).containsExactly("디저트");
        assertThat(requestOnly.avoidTastes()).containsExactly(Taste.SPICY);
        assertThat(requestOnly.maxMinutes()).isEqualTo(30);
        assertThat(requestOnly.excludeIngredientIds()).containsExactly(7L);
        assertThat(requestOnly.excludeCategories()).containsExactly("양념", "음료");
    }

    @Test
    @DisplayName("선택 재료만 쓰기면 요청만 검색에도 선택 재료를 남긴다 (꼭 들어 있어야 하므로)")
    void keepsSelected_whenRequestOnlyWithSelectedOnly() {
        Condition condition = RecipeCandidateConditions.from(
                RecipeQuery.of(MEMBER_ID, List.of(1L), true), List.of(), EXCLUDED);

        Condition requestOnly = RecipeCandidateConditions.requestOnly(condition);

        assertThat(requestOnly.selectedIngredientIds()).containsExactly(1L);
        assertThat(requestOnly.selectedOnly()).isTrue();
    }

    @Test
    @DisplayName("요리 이름 키워드가 있으면 요청만 검색은 피할 맛 없이 찾는다 (매운 거 말고 김치찌개의 김치찌개가 검색에서 빠지지 않게)")
    void dropsAvoidTastes_whenRequestOnlyWithKeywords() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), null, List.of("찌개"), null, null, false,
                List.of("김치찌개"), "김치찌개").withAvoidTastes(List.of(Taste.SPICY));
        Condition condition = RecipeCandidateConditions.from(query, List.of(), EXCLUDED);

        assertThat(condition.avoidTastes()).containsExactly(Taste.SPICY);
        assertThat(RecipeCandidateConditions.requestOnly(condition).avoidTastes()).isEmpty();
    }
}
