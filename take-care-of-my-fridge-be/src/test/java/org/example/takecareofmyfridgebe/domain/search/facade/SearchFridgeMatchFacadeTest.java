package org.example.takecareofmyfridgebe.domain.search.facade;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeIngredientInfo;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchResponse;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchResponse.RecipeMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchFridgeMatchFacadeTest {

    private static final long MEMBER_ID = 7L;
    // 재료 ID: 1 대파, 2 두부, 3 소금(기본 양념), 4 애호박
    private static final FridgeIngredientInfo GREEN_ONION = new FridgeIngredientInfo(1L, "대파", "국산 흙대파");
    private static final FridgeIngredientInfo SALT = new FridgeIngredientInfo(3L, "소금", "천일염");

    @Mock
    private RecipeService recipeService;
    @Mock
    private FridgeService fridgeService;
    @Mock
    private IngredientService ingredientService;
    @InjectMocks
    private SearchFridgeMatchFacade facade;

    @Test
    @DisplayName("레시피 재료 중 기본 양념 등은 빼고, 남은 재료 중 냉장고에 있는 수를 요청 순서대로 센다")
    void match_countsOwnedIngredients_excludingSeasoning() {
        // Given
        when(fridgeService.findUsableIngredients(MEMBER_ID)).thenReturn(List.of(GREEN_ONION, SALT));
        when(recipeService.findIngredientIds(List.of(20L, 10L))).thenReturn(Map.of(
                10L, Set.of(1L, 2L, 3L),
                20L, Set.of(2L, 4L)));
        when(ingredientService.getExcludedIngredientIds(any())).thenReturn(Set.of(3L));

        // When
        SearchFridgeMatchResponse response = facade.match(MEMBER_ID, new SearchFridgeMatchRequest(List.of(20L, 10L), null));

        // Then
        assertThat(response.recipes()).containsExactly(
                new RecipeMatch(20L, 0, 2),
                new RecipeMatch(10L, 1, 2));
        assertThat(response.keywordItems()).isEmpty();
    }

    @Test
    @DisplayName("재료 정보가 없는 레시피는 0/0으로 돌려준다")
    void match_returnsZero_whenRecipeHasNoIngredients() {
        // Given
        when(fridgeService.findUsableIngredients(MEMBER_ID)).thenReturn(List.of(GREEN_ONION));
        when(recipeService.findIngredientIds(List.of(30L))).thenReturn(Map.of());
        when(ingredientService.getExcludedIngredientIds(any())).thenReturn(Set.of());

        // When & Then
        assertThat(facade.match(MEMBER_ID, new SearchFridgeMatchRequest(List.of(30L), null)).recipes())
                .containsExactly(new RecipeMatch(30L, 0, 0));
    }

    @Test
    @DisplayName("검색어가 입력한 이름에 들었거나 표준 재료 이름과 같은 냉장고 재료를 한 번씩 돌려준다")
    void match_returnsKeywordItems_whenKeywordInFridge() {
        // Given
        FridgeIngredientInfo anotherGreenOnion = new FridgeIngredientInfo(1L, "대파", "실파");
        when(fridgeService.findUsableIngredients(MEMBER_ID))
                .thenReturn(List.of(GREEN_ONION, GREEN_ONION, anotherGreenOnion, SALT));
        when(recipeService.findIngredientIds(any())).thenReturn(Map.of());
        when(ingredientService.getExcludedIngredientIds(any())).thenReturn(Set.of());

        // When
        SearchFridgeMatchResponse response = facade.match(MEMBER_ID, new SearchFridgeMatchRequest(List.of(10L), "대파"));

        // Then
        assertThat(response.keywordItems()).containsExactly("국산 흙대파", "실파");
    }
}
