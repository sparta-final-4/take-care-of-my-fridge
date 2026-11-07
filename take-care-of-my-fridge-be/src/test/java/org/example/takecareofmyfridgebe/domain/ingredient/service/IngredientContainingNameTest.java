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

// 이름이 들어 있는 재료 찾기 (IngredientService.findContainingNames, 알레르기 재료 넓히기)
class IngredientContainingNameTest {

    private IngredientRepository ingredientRepository;
    private IngredientService ingredientService;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        ingredientService = new IngredientService(ingredientRepository, new IngredientMatchProperties(1.0, 2));
    }

    @Test
    @DisplayName("두 글자 이상이면 이름에 그 이름이 들어 있는 재료를 ID 순으로 돌려준다 (땅콩 → 땅콩버터·땅콩가루)")
    void returnsIngredientsContainingName() {
        when(ingredientRepository.findByNameContaining("땅콩")).thenReturn(List.of(
                ingredient(30L, "ING_30", "땅콩버터"), ingredient(10L, "ING_10", "땅콩"), ingredient(20L, "ING_20", "땅콩가루")));

        Map<String, List<Ingredient>> result = ingredientService.findContainingNames(List.of(" 땅콩 "));

        assertThat(result).containsOnlyKeys("땅콩");
        assertThat(result.get("땅콩")).extracting(Ingredient::getId).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("한 글자면 들어 있는 것으로 보지 않고 그 글자로 시작하는 재료만 돌려준다 (게 → 게·게살, 바게트는 아님)")
    void returnsIngredientsStartingWithName_whenOneLetter() {
        when(ingredientRepository.findByNameStartingWith("게")).thenReturn(List.of(
                ingredient(1L, "ING_1", "게"), ingredient(2L, "ING_2", "게살")));

        assertThat(ingredientService.findContainingNames(List.of("게")).get("게"))
                .extracting(Ingredient::getName).containsExactly("게", "게살");
        verify(ingredientRepository, never()).findByNameContaining(anyString());
    }

    @Test
    @DisplayName("직접 입력·밀키트 재료는 빼고, 이름이 없으면 조회하지 않는다")
    void excludesCustomAndMealkit_andSkipsEmpty() {
        when(ingredientRepository.findByNameContaining("새우")).thenReturn(List.of(
                ingredient(5L, "ING_5", "새우젓"), ingredient(6L, Ingredient.ING_CUSTOM, "새우"),
                ingredient(7L, Ingredient.ING_MEALKIT, "새우밀키트")));

        assertThat(ingredientService.findContainingNames(List.of("새우")).get("새우"))
                .extracting(Ingredient::getId).containsExactly(5L);
        assertThat(ingredientService.findContainingNames(List.of())).isEmpty();
    }

    private static Ingredient ingredient(Long id, String code, String name) {
        Ingredient ingredient = Ingredient.builder().ingredientCode(code).name(name).baseUnit("g").build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
