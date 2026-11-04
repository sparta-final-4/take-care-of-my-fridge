package org.example.takecareofmyfridgebe.domain.ingredient.service;

import org.example.takecareofmyfridgebe.domain.ingredient.config.IngredientMatchProperties;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 같은 종류 재료 묶기 (IngredientService.findSameKindIdsByNames)
class IngredientSameKindTest {

    private IngredientRepository ingredientRepository;
    private IngredientService ingredientService;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        ingredientService = new IngredientService(ingredientRepository, new IngredientMatchProperties(1.0, 2));
    }

    @Test
    @DisplayName("이름이 그 이름으로 끝나는 재료를 같은 종류로 묶는다 (버섯 → 버섯류)")
    void groupsIngredientsEndingWithName() {
        when(ingredientRepository.findByNameEndingWith("버섯")).thenReturn(List.of(
                ingredient(161L, "ING_RCP_0161", "표고버섯"),
                ingredient(45L, "ING_RCP_0045", "버섯"),
                ingredient(157L, "ING_RCP_0157", "양송이버섯")));

        Map<String, List<Long>> result = ingredientService.findSameKindIdsByNames(List.of(" 버섯 "));

        assertThat(result).containsOnlyKeys("버섯");
        assertThat(result.get("버섯")).containsExactly(45L, 157L, 161L);
    }

    @Test
    @DisplayName("한 글자 이름은 넓히지 않고 이름이 같은 재료만 묶는다")
    void keepsExactName_whenNameIsOneLetter() {
        when(ingredientRepository.findByNameIn(List.of("파"))).thenReturn(List.of(ingredient(7L, "ING_RCP_0007", "파")));

        Map<String, List<Long>> result = ingredientService.findSameKindIdsByNames(List.of("파"));

        assertThat(result.get("파")).containsExactly(7L);
        verify(ingredientRepository, never()).findByNameEndingWith(anyString());
    }

    @Test
    @DisplayName("직접 입력·밀키트 재료는 묶음에서 뺀다")
    void excludesCustomAndMealkit() {
        when(ingredientRepository.findByNameEndingWith("고기")).thenReturn(List.of(
                ingredient(43L, "ING_RCP_0043", "소고기"),
                ingredient(1L, Ingredient.ING_CUSTOM, "고기"),
                ingredient(2L, Ingredient.ING_MEALKIT, "밀키트고기")));

        assertThat(ingredientService.findSameKindIdsByNames(List.of("고기")).get("고기")).containsExactly(43L);
    }

    @Test
    @DisplayName("이름이 없거나 비어 있으면 조회하지 않고 빈 결과를 돌려준다")
    void returnsEmpty_whenNoNames() {
        assertThat(ingredientService.findSameKindIdsByNames(List.of())).isEmpty();
        assertThat(ingredientService.findSameKindIdsByNames(List.of(" "))).isEmpty();
        verify(ingredientRepository, never()).findByNameEndingWith(anyString());
    }

    private static Ingredient ingredient(Long id, String code, String name) {
        Ingredient ingredient = Ingredient.builder().ingredientCode(code).name(name).baseUnit("g").build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
