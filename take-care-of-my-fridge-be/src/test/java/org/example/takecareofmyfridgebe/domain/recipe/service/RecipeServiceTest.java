package org.example.takecareofmyfridgebe.domain.recipe.service;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCreateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeNutritionRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdatedAtDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdateRequest;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private IngredientService ingredientService;
    @InjectMocks
    private RecipeService service;

    @Test
    @DisplayName("1부터 시작하는 요청 페이지를 0부터 시작하는 PageRequest로 바꾸고 검색 조건을 그대로 넘긴다")
    void getRecipes_convertsPageAndPassesCondition() {
        // given
        RecipeListRequest request = new RecipeListRequest(
                RecipeSort.DIFFICULTY_ASC, "찌개", "한식", "국", 30, 5, 3, 10);
        when(recipeRepository.findRecipesByCondition(any(RecipeListRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        service.getRecipes(request);

        // then
        verify(recipeRepository).findRecipesByCondition(request, PageRequest.of(2, 10));
    }

    @Test
    @DisplayName("조회한 레시피를 목록 응답 DTO와 1부터 시작하는 페이지 정보로 변환한다")
    void getRecipes_mapsToResponse() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findRecipesByCondition(any(RecipeListRequest.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stew), PageRequest.of(0, 20), 1));
        RecipeListRequest request = new RecipeListRequest(null, null, null, null, null, null, null, null);

        // when
        PageResponse<RecipeListItemResponse> result = service.getRecipes(request);

        // then
        assertThat(result.pageNumber()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).containsExactly(new RecipeListItemResponse(
                7L, "된장찌개", "된장찌개 만드는 법", null, "한식", "국", 30, 2, 3, new BigDecimal("350.00")));
    }

    @Test
    @DisplayName("상세 조회 시 레시피, 영양정보, 재료(수량·기본양념 여부 포함), 조리 단계를 DTO로 매핑한다")
    void getRecipe_mapsToResponse() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findById(7L)).thenReturn(Optional.of(stew));
        when(recipeIngredientRepository.findByRecipeIdOrderBySeqAsc(7L)).thenReturn(List.of(
                RecipeFixture.createIngredient(stew, 1, "된장", "2큰술", new BigDecimal("30.00")),
                RecipeFixture.createIngredient(stew, 2, "두부", "1/2모", null)));
        when(recipeStepRepository.findByRecipeIdOrderByStepNoAsc(7L)).thenReturn(List.of(
                RecipeFixture.createStep(stew, 1, "물을 끓인다."),
                RecipeFixture.createStep(stew, 2, "된장을 푼다.")));
        when(ingredientService.findBasicSeasoningIds(List.of(1L, 2L))).thenReturn(Set.of(1L));

        // when
        RecipeDetailResponse result = service.getRecipe(7L);

        // then
        assertThat(result.recipeId()).isEqualTo(7L);
        assertThat(result.nutrition().calories()).isEqualByComparingTo("350");
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::displayName)
                .containsExactly("된장", "두부");
        assertThat(result.ingredients().get(0).quantity()).isEqualByComparingTo("30.00");
        assertThat(result.ingredients().get(0).basicSeasoning()).isTrue();
        assertThat(result.ingredients().get(1).quantity()).isNull();
        assertThat(result.ingredients().get(1).basicSeasoning()).isFalse();
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

    @Test
    @DisplayName("레시피 등록 시 출처를 ADMIN으로 정하고, 재료와 조리 단계에 목록 순서대로 번호를 매겨 저장한다")
    void createRecipe_savesWithOrder() {
        // given
        when(ingredientRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(mock(Ingredient.class), mock(Ingredient.class)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipeIngredientRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipeStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingredientService.findBasicSeasoningIds(List.of(10L, 20L))).thenReturn(Set.of(10L));

        // when
        RecipeDetailResponse result = service.createRecipe(RecipeFixture.createRecipeRequest(10L, 20L));

        // then
        assertThat(result.name()).isEqualTo("된장찌개");
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::seq)
                .containsExactly(1, 2);
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::ingredientId)
                .containsExactly(10L, 20L);
        assertThat(result.steps()).extracting(RecipeDetailResponse.StepItem::stepNo)
                .containsExactly(1, 2);
        verify(recipeRepository).save(argThat(recipe ->
                recipe.getSource().equals("ADMIN") && recipe.getSourceId().length() == 20));
    }

    @Test
    @DisplayName("레시피 등록 응답에도 재료의 basicSeasoning과 quantity가 정확히 매핑된다")
    void createRecipe_responseIncludesBasicSeasoningAndQuantity() {
        // given
        when(ingredientRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(mock(Ingredient.class), mock(Ingredient.class)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipeIngredientRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipeStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingredientService.findBasicSeasoningIds(List.of(10L, 20L))).thenReturn(Set.of(10L));

        RecipeCreateRequest request = new RecipeCreateRequest(
                "된장찌개", "구수한 찌개", null, "한식", "국", "끓이기", null,
                30, 2, 3, null, null, null,
                List.of(
                        new RecipeIngredientRequest(10L, "된장", "2큰술", new BigDecimal("30.00"), "양념", null),
                        new RecipeIngredientRequest(20L, "두부", "1/2모", null, "주재료", null)
                ),
                List.of(new RecipeStepRequest("물을 끓인다.", null, null))
        );

        // when
        RecipeDetailResponse result = service.createRecipe(request);

        // then
        assertThat(result.ingredients()).hasSize(2);
        assertThat(result.ingredients().get(0).ingredientId()).isEqualTo(10L);
        assertThat(result.ingredients().get(0).quantity()).isEqualByComparingTo("30.00");
        assertThat(result.ingredients().get(0).basicSeasoning()).isTrue();
        assertThat(result.ingredients().get(1).ingredientId()).isEqualTo(20L);
        assertThat(result.ingredients().get(1).quantity()).isNull();
        assertThat(result.ingredients().get(1).basicSeasoning()).isFalse();
    }

    @Test
    @DisplayName("없는 재료가 섞여 있으면 INGREDIENT_NOT_FOUND 예외가 발생하고 아무것도 저장하지 않는다")
    void createRecipe_ingredientNotFound() {
        // given
        when(ingredientRepository.findAllById(List.of(10L, 99L))).thenReturn(List.of(mock(Ingredient.class)));
        RecipeCreateRequest request = RecipeFixture.createRecipeRequest(10L, 99L);

        // when & then
        assertThatThrownBy(() -> service.createRecipe(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INGREDIENT_NOT_FOUND);
        verify(recipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("레시피 수정은 보낸 필드만 바꾸고, 재료와 조리 단계를 보내지 않으면 기존 목록을 그대로 둔다")
    void updateRecipe_changesOnlySentFields() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findById(7L)).thenReturn(Optional.of(stew));
        when(recipeIngredientRepository.findByRecipeIdOrderBySeqAsc(7L))
                .thenReturn(List.of(RecipeFixture.createIngredient(stew, 1, "된장", "2큰술")));
        when(recipeStepRepository.findByRecipeIdOrderByStepNoAsc(7L))
                .thenReturn(List.of(RecipeFixture.createStep(stew, 1, "물을 끓인다.")));
        when(ingredientService.findBasicSeasoningIds(List.of(1L))).thenReturn(Set.of(1L));
        RecipeNutritionRequest nutrition = new RecipeNutritionRequest(null, new BigDecimal("400"), null, null, null, null);
        RecipeUpdateRequest request = updateRequest("차돌 된장찌개", nutrition, null);

        // when
        RecipeDetailResponse result = service.updateRecipe(7L, request);

        // then
        assertThat(result.name()).isEqualTo("차돌 된장찌개");
        assertThat(result.summary()).isEqualTo("된장찌개 만드는 법");
        assertThat(result.nutrition().calories()).isEqualByComparingTo("400");
        assertThat(result.nutrition().sodiumMg()).isNull();
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::displayName)
                .containsExactly("된장");
        verify(recipeIngredientRepository, never()).deleteAllByRecipeId(anyLong());
        verify(recipeStepRepository, never()).deleteAllByRecipeId(anyLong());
    }

    @Test
    @DisplayName("재료를 보내면 기존 재료를 모두 지우고 새 목록을 순서 번호를 다시 매겨 저장한다")
    void updateRecipe_replacesIngredients() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findById(7L)).thenReturn(Optional.of(stew));
        when(ingredientRepository.findAllById(List.of(30L, 40L)))
                .thenReturn(List.of(mock(Ingredient.class), mock(Ingredient.class)));
        when(recipeIngredientRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingredientService.findBasicSeasoningIds(List.of(30L, 40L))).thenReturn(Set.of());
        RecipeUpdateRequest request = updateRequest(null, null, List.of(
                new RecipeIngredientRequest(30L, "차돌박이", "100g", null, "주재료", null),
                new RecipeIngredientRequest(40L, "애호박", "1/2개", null, "부재료", null)));

        // when
        RecipeDetailResponse result = service.updateRecipe(7L, request);

        // then
        verify(recipeIngredientRepository).deleteAllByRecipeId(7L);
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::seq)
                .containsExactly(1, 2);
        assertThat(result.ingredients()).extracting(RecipeDetailResponse.IngredientItem::ingredientId)
                .containsExactly(30L, 40L);
        assertThat(result.name()).isEqualTo("된장찌개");
    }

    @Test
    @DisplayName("레시피 수정 응답에도 교체된 재료의 basicSeasoning과 quantity가 정확히 매핑된다")
    void updateRecipe_responseIncludesBasicSeasoningAndQuantity() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findById(7L)).thenReturn(Optional.of(stew));
        when(ingredientRepository.findAllById(List.of(30L, 40L)))
                .thenReturn(List.of(mock(Ingredient.class), mock(Ingredient.class)));
        when(recipeIngredientRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingredientService.findBasicSeasoningIds(List.of(30L, 40L))).thenReturn(Set.of(40L));

        RecipeUpdateRequest request = updateRequest(null, null, List.of(
                new RecipeIngredientRequest(30L, "차돌박이", "100g", new BigDecimal("100.00"), "주재료", null),
                new RecipeIngredientRequest(40L, "소금", "1작은술", null, "양념", null)));

        // when
        RecipeDetailResponse result = service.updateRecipe(7L, request);

        // then
        assertThat(result.ingredients()).hasSize(2);
        assertThat(result.ingredients().get(0).ingredientId()).isEqualTo(30L);
        assertThat(result.ingredients().get(0).quantity()).isEqualByComparingTo("100.00");
        assertThat(result.ingredients().get(0).basicSeasoning()).isFalse();
        assertThat(result.ingredients().get(1).ingredientId()).isEqualTo(40L);
        assertThat(result.ingredients().get(1).quantity()).isNull();
        assertThat(result.ingredients().get(1).basicSeasoning()).isTrue();
    }

    @Test
    @DisplayName("교체할 재료 중 없는 재료가 있으면 INGREDIENT_NOT_FOUND 예외가 발생하고 기존 재료를 지우지 않는다")
    void updateRecipe_ingredientNotFound() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findById(7L)).thenReturn(Optional.of(stew));
        when(ingredientRepository.findAllById(List.of(99L))).thenReturn(List.of());
        RecipeUpdateRequest request = updateRequest(null, null, List.of(
                new RecipeIngredientRequest(99L, "없는 재료", null, null, null, null)));

        // when & then
        assertThatThrownBy(() -> service.updateRecipe(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INGREDIENT_NOT_FOUND);
        verify(recipeIngredientRepository, never()).deleteAllByRecipeId(anyLong());
        assertThat(stew.getName()).isEqualTo("된장찌개");
    }

    @Test
    @DisplayName("없는 레시피를 수정하면 RECIPE_NOT_FOUND 예외가 발생한다")
    void updateRecipe_notFound() {
        // given
        when(recipeRepository.findById(99L)).thenReturn(Optional.empty());
        RecipeUpdateRequest request = updateRequest("새 이름", null, null);

        // when & then
        assertThatThrownBy(() -> service.updateRecipe(99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
    }

    private static RecipeUpdateRequest updateRequest(String name, RecipeNutritionRequest nutrition,
                                                     List<RecipeIngredientRequest> ingredients) {
        return new RecipeUpdateRequest(name, null, null, null, null, null, null, null, null, null, null, null,
                nutrition, ingredients, null);
    }

    /**
     * 레시피 ID가 존재하면 existsById는 true를 반환한다.
     */
    @Test
    void existsByIdReturnsTrueWhenRecipeExists() {
        // Given
        Long recipeId = 1L;
        when(recipeRepository.existsById(recipeId)).thenReturn(true);

        // When
        boolean exists = service.existsById(recipeId);

        // Then
        assertTrue(exists);
        verify(recipeRepository).existsById(recipeId);
    }

    /**
     * 레시피 ID가 존재하지 않으면 existsById는 false를 반환한다.
     */
    @Test
    void existsByIdReturnsFalseWhenRecipeDoesNotExist() {
        // Given
        Long recipeId = 99L;
        when(recipeRepository.existsById(recipeId)).thenReturn(false);

        // When
        boolean exists = service.existsById(recipeId);

        // Then
        assertFalse(exists);
        verify(recipeRepository).existsById(recipeId);
    }

    /**
     * 레시피 ID가 null이면 existsById는 저장소 조회 없이 false를 반환한다.
     */
    @Test
    void existsByIdReturnsFalseWhenIdIsNull() {
        // When
        boolean exists = service.existsById(null);

        // Then
        assertFalse(exists);
        verify(recipeRepository, never()).existsById(anyLong());
    }

    /**
     * 여러 레시피 ID 목록으로 요약 정보를 조회하면 존재하는 ID만 Map으로 반환된다.
     */
    @Test
    void findSummariesReturnsMappedSummariesForExistingIds() {
        // Given
        Recipe recipe1 = RecipeFixture.createRecipe(1L, "된장찌개");
        Recipe recipe2 = RecipeFixture.createRecipe(2L, "김치찌개");
        when(recipeRepository.findAllById(List.of(1L, 2L, 99L)))
                .thenReturn(List.of(recipe1, recipe2));

        // When
        Map<Long, RecipeSummary> summaries = service.findSummaries(List.of(1L, 2L, 99L));

        // Then
        assertEquals(2, summaries.size());
        assertEquals(new RecipeSummary(1L, "된장찌개", recipe1.getImageUrl(), recipe1.getTipSummary()), summaries.get(1L));
        assertEquals(new RecipeSummary(2L, "김치찌개", recipe2.getImageUrl(), recipe2.getTipSummary()), summaries.get(2L));
        assertFalse(summaries.containsKey(99L));
    }

    /**
     * 빈 ID 목록이나 null로 요약 정보를 조회하면 저장소 호출 없이 빈 Map을 반환한다.
     */
    @Test
    void findSummariesReturnsEmptyMapWhenIdsEmptyOrNull() {
        // When & Then
        assertTrue(service.findSummaries(List.of()).isEmpty());
        assertTrue(service.findSummaries(null).isEmpty());
        verify(recipeRepository, never()).findAllById(any());
    }

    /**
     * 중복 ID나 null 요소가 포함된 목록으로 요약 정보를 조회해도
     * 중복 제거 및 null 제외 후 안전하게 조회된다.
     */
    @Test
    void findSummariesHandlesDuplicatesAndNullsSafely() {
        // Given
        Recipe recipe1 = RecipeFixture.createRecipe(1L, "된장찌개");
        when(recipeRepository.findAllById(List.of(1L))).thenReturn(List.of(recipe1));

        // When
        Map<Long, RecipeSummary> summaries = service.findSummaries(java.util.Arrays.asList(1L, 1L, null));

        // Then
        assertEquals(1, summaries.size());
        assertEquals(new RecipeSummary(1L, "된장찌개", recipe1.getImageUrl(), recipe1.getTipSummary()), summaries.get(1L));
        verify(recipeRepository).findAllById(List.of(1L));
    }

    @Test
    @DisplayName("후기 요약이 null이거나 공백이면 IllegalArgumentException이 발생한다")
    void updateTipSummaryThrowsExceptionWhenSummaryIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.updateTipSummary(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.updateTipSummary(1L, ""));
        assertThrows(IllegalArgumentException.class, () -> service.updateTipSummary(1L, "   "));
        verify(recipeRepository, never()).updateTipSummary(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 레시피의 후기 요약을 변경하려 하면 RECIPE_NOT_FOUND 예외가 발생한다")
    void updateTipSummaryThrowsNotFoundWhenRecipeDoesNotExist() {
        // Given
        Long recipeId = 999L;
        when(recipeRepository.updateTipSummary(eq(recipeId), any(), any()))
                .thenReturn(0);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateTipSummary(recipeId, "새로운 요약")
        );
        assertEquals(ErrorCode.RECIPE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("정상적인 요약 내용 전달 시 레시피의 tipSummary가 업데이트된다")
    void updateTipSummaryUpdatesSuccessfully() {
        // Given
        Long recipeId = 1L;
        String summary = "간장을 줄이고 마늘을 더 넣으면 맛있어요.";
        when(recipeRepository.updateTipSummary(eq(recipeId), eq(summary), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        service.updateTipSummary(recipeId, summary);

        // Then
        verify(recipeRepository).updateTipSummary(eq(recipeId), eq(summary), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("빈 ID 목록이나 null로 수정 시각을 조회하면 저장소 호출 없이 빈 Map을 반환한다")
    void findUpdatedAtsReturnsEmptyMapWhenIdsEmptyOrNull() {
        // When & Then
        assertTrue(service.findUpdatedAts(List.of()).isEmpty());
        assertTrue(service.findUpdatedAts(null).isEmpty());
        verify(recipeRepository, never()).findUpdatedAtsByIdIn(any());
    }

    @Test
    @DisplayName("레시피 ID 목록으로 최근 수정 시각을 조회하면 중복을 제거하고 ID를 키로 하는 Map을 반환한다")
    void findUpdatedAtsHandlesDuplicatesAndReturnsMap() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 10, 25, 10, 0);
        RecipeUpdatedAtDto dto1 = new RecipeUpdatedAtDto(1L, time1);
        RecipeUpdatedAtDto dto2 = new RecipeUpdatedAtDto(2L, time2);

        when(recipeRepository.findUpdatedAtsByIdIn(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(1L, 2L)))))
                .thenReturn(List.of(dto1, dto2));

        // When
        Map<Long, LocalDateTime> result = service.findUpdatedAts(java.util.Arrays.asList(1L, 2L, 1L, null));

        // Then
        assertEquals(2, result.size());
        assertEquals(time1, result.get(1L));
        assertEquals(time2, result.get(2L));
        verify(recipeRepository).findUpdatedAtsByIdIn(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(1L, 2L))));
    }
}
