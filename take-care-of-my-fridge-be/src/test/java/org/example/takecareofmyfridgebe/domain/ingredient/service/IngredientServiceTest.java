package org.example.takecareofmyfridgebe.domain.ingredient.service;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * IngredientService 단위 테스트.
 * 요리 완료 시 차감에서 제외되어야 할 재료(기본 양념, 직접 입력 ING_CUSTOM, 밀키트 ING_MEALKIT)는
 * 제외 목록에 포함되고 일반 재료는 빠지는지, null 또는 빈 컬렉션에 대한 방어 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientService ingredientService;

    /**
     * 차감 제외 대상(기본 양념, ING_CUSTOM, ING_MEALKIT)은 Set에 포함되고 일반 재료(두부, 양파)는 빠진다.
     */
    @Test
    void getExcludedIngredientIdsFiltersExcludedIngredients() {
        // Given: 기본 양념, 직접 입력, 밀키트, 일반 재료 생성
        Ingredient basicSeasoning = createIngredient(1L, "ING_SALT", "소금", true);
        Ingredient customIngredient = createIngredient(2L, Ingredient.ING_CUSTOM, "직접입력재료", false);
        Ingredient mealkitIngredient = createIngredient(3L, Ingredient.ING_MEALKIT, "밀키트", false);
        Ingredient normalIngredient1 = createIngredient(4L, "ING_TOFU", "두부", false);
        Ingredient normalIngredient2 = createIngredient(5L, "ING_ONION", "양파", false);

        List<Long> requestedIds = List.of(1L, 2L, 3L, 4L, 5L);
        when(ingredientRepository.findAllById(requestedIds))
                .thenReturn(List.of(basicSeasoning, customIngredient, mealkitIngredient, normalIngredient1, normalIngredient2));

        // When: 제외 대상 재료 ID 조회
        Set<Long> excludedIds = ingredientService.getExcludedIngredientIds(requestedIds);

        // Then: 제외 대상만 포함되고 일반 재료는 빠짐
        assertEquals(3, excludedIds.size());
        assertTrue(excludedIds.contains(1L));
        assertTrue(excludedIds.contains(2L));
        assertTrue(excludedIds.contains(3L));
        assertFalse(excludedIds.contains(4L));
        assertFalse(excludedIds.contains(5L));
    }

    /**
     * 재료 ID 목록이 null이거나 비어있으면 빈 Set을 반환한다.
     */
    @Test
    void getExcludedIngredientIdsReturnsEmptySetWhenInputIsNullOrEmpty() {
        // When & Then
        assertTrue(ingredientService.getExcludedIngredientIds(null).isEmpty());
        assertTrue(ingredientService.getExcludedIngredientIds(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("기본 양념(isBasicSeasoning=true)인 재료만 Set에 포함된다")
    void findBasicSeasoningIdsFiltersBasicSeasoningsOnly() {
        // Given
        Ingredient basicSeasoning = createIngredient(1L, "ING_SALT", "소금", true);
        Ingredient customIngredient = createIngredient(2L, Ingredient.ING_CUSTOM, "직접입력재료", false);
        Ingredient mealkitIngredient = createIngredient(3L, Ingredient.ING_MEALKIT, "밀키트", false);
        Ingredient normalIngredient = createIngredient(4L, "ING_TOFU", "두부", false);

        List<Long> requestedIds = List.of(1L, 2L, 3L, 4L);
        when(ingredientRepository.findAllById(requestedIds))
                .thenReturn(List.of(basicSeasoning, customIngredient, mealkitIngredient, normalIngredient));

        // When
        Set<Long> basicIds = ingredientService.findBasicSeasoningIds(requestedIds);

        // Then
        assertEquals(1, basicIds.size());
        assertTrue(basicIds.contains(1L));
        assertFalse(basicIds.contains(2L));
        assertFalse(basicIds.contains(3L));
        assertFalse(basicIds.contains(4L));
    }

    @Test
    @DisplayName("기본 양념 조회 시 재료 ID 목록이 null이거나 비어있으면 빈 Set을 반환한다")
    void findBasicSeasoningIdsReturnsEmptySetWhenInputIsNullOrEmpty() {
        // When & Then
        assertTrue(ingredientService.findBasicSeasoningIds(null).isEmpty());
        assertTrue(ingredientService.findBasicSeasoningIds(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("재료 ID 목록으로 재료를 한 번에 조회해 ID를 키로 돌려주고, 비어 있으면 조회하지 않는다")
    void getIngredientsByIdsReturnsMapById() {
        // Given
        Ingredient onion = createIngredient(4L, "ING_ONION", "양파", false);
        Ingredient tofu = createIngredient(5L, "ING_TOFU", "두부", false);
        when(ingredientRepository.findAllById(Set.of(4L, 5L))).thenReturn(List.of(onion, tofu));

        // When
        Map<Long, Ingredient> result = ingredientService.getIngredientsByIds(Set.of(4L, 5L));

        // Then
        assertEquals(Map.of(4L, onion, 5L, tofu), result);
        assertTrue(ingredientService.getIngredientsByIds(Collections.emptySet()).isEmpty());
    }

    @Test
    @DisplayName("밀키트용 기본 재료(ING_MEALKIT)를 조회하고, 없으면 MEALKIT_INGREDIENT_NOT_CONFIGURED 예외를 던진다")
    void getMealkitIngredientReturnsOrThrows() {
        // Given
        Ingredient mealkit = createIngredient(3L, Ingredient.ING_MEALKIT, "밀키트", false);
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_MEALKIT))
                .thenReturn(Optional.of(mealkit))
                .thenReturn(Optional.empty());

        // When & Then
        assertSame(mealkit, ingredientService.getMealkitIngredient());
        BusinessException exception = assertThrows(BusinessException.class, ingredientService::getMealkitIngredient);
        assertEquals(ErrorCode.MEALKIT_INGREDIENT_NOT_CONFIGURED, exception.getErrorCode());
    }

    private Ingredient createIngredient(Long id, String code, String name, boolean basicSeasoning) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(basicSeasoning)
                .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
