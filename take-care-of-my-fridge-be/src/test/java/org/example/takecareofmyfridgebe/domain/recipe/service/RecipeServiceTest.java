package org.example.takecareofmyfridgebe.domain.recipe.service;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeIngredientRepository;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeRepository;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeStepRepository;
import org.example.takecareofmyfridgebe.dummy.RecipeFixture;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;
    @Mock
    private RecipeStepRepository recipeStepRepository;
    @InjectMocks
    private RecipeService service;

    @Test
    @DisplayName("1부터 시작하는 요청 페이지를 0부터 시작하는 PageRequest로 바꾸고 정렬을 적용한다")
    void getRecipes_convertsPageAndSort() {
        // given
        when(recipeRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // when
        service.getRecipes(RecipeSort.DIFFICULTY_ASC, 3, 10);

        // then
        verify(recipeRepository).findAll(PageRequest.of(2, 10, RecipeSort.DIFFICULTY_ASC.toSort()));
    }

    @Test
    @DisplayName("조회한 레시피를 목록 응답 DTO와 1부터 시작하는 페이지 정보로 변환한다")
    void getRecipes_mapsToResponse() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stew), PageRequest.of(0, 20), 1));

        // when
        PageResponse<RecipeListItemResponse> result = service.getRecipes(RecipeSort.LATEST, 1, 20);

        // then
        assertThat(result.pageNumber()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).containsExactly(new RecipeListItemResponse(
                7L, "된장찌개", "된장찌개 만드는 법", null, "한식", "국", 30, 2, 3, new BigDecimal("350.00")));
    }

    @Test
    @DisplayName("레시피 상세는 영양 정보와 재료·조리 단계를 순서대로 함께 반환한다")
    void getRecipe_withIngredientsAndSteps() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findById(7L)).thenReturn(Optional.of(stew));
        when(recipeIngredientRepository.findByRecipeIdOrderBySeqAsc(7L)).thenReturn(List.of(
                RecipeFixture.createIngredient(stew, 1, "된장", "2큰술"),
                RecipeFixture.createIngredient(stew, 2, "두부", "1/2모")));
        when(recipeStepRepository.findByRecipeIdOrderByStepNoAsc(7L)).thenReturn(List.of(
                RecipeFixture.createStep(stew, 1, "물을 끓인다."),
                RecipeFixture.createStep(stew, 2, "된장을 푼다.")));

        // when
        RecipeDetailResponse result = service.getRecipe(7L);

        // then
        assertThat(result.recipeId()).isEqualTo(7L);
        assertThat(result.nutrition().calories()).isEqualByComparingTo("350");
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::displayName)
                .containsExactly("된장", "두부");
        assertThat(result.steps()).extracting(RecipeDetailResponse.StepItem::stepNo)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("없는 레시피면 RECIPE_NOT_FOUND 예외가 발생하고 재료·단계는 조회하지 않는다")
    void getRecipe_notFound() {
        // given
        when(recipeRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getRecipe(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
        verify(recipeIngredientRepository, never()).findByRecipeIdOrderBySeqAsc(anyLong());
        verify(recipeStepRepository, never()).findByRecipeIdOrderByStepNoAsc(anyLong());
    }
}
