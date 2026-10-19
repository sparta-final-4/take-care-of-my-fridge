package org.example.takecareofmyfridgebe.domain.ingredient.service;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IngredientMatcher 단위 테스트.
 * 재료명 다듬기(normalize), 사전 일치 매핑, 미등록 재료의 ING_CUSTOM 폴백,
 * 그리고 ING_CUSTOM 미설정 시 예외 발생을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class IngredientMatcherTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientMatcher ingredientMatcher;

    /**
     * 괄호 안 문구([특가], (500g))와 숫자+단위(10구, 300g, 2개입)가 제거되고
     * 앞뒤·연속 공백이 깔끔하게 정리되는지 검증한다.
     */
    @Test
    void normalizeRemovesBracketsAndQuantityUnits() {
        assertEquals("무항생제 신선란", IngredientMatcher.normalize("[특가] 무항생제 신선란 10구"));
        assertEquals("국산 두부", IngredientMatcher.normalize("국산 두부 300g"));
        assertEquals("비비고 썰은배추김치", IngredientMatcher.normalize("비비고 썰은배추김치(500g)"));
        assertEquals("양파", IngredientMatcher.normalize("(국산) 양파 2개입"));
    }

    /**
     * 다듬은 재료명이 표준 재료 사전(ingredients.name)에 있으면
     * ING_CUSTOM을 조회하지 않고 해당 표준 재료를 그대로 반환하는지 검증한다.
     */
    @Test
    void matchReturnsExactIngredientWhenFoundInDictionary() {
        // Given: 다듬은 이름 "국산 두부"가 사전에 등록되어 있음
        Ingredient tofu = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("국산 두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByName("국산 두부")).thenReturn(Optional.of(tofu));

        // When: 원문 "국산 두부 300g"으로 매핑 요청
        Ingredient matched = ingredientMatcher.match("국산 두부 300g");

        // Then: 사전의 두부 엔티티가 반환되고 ING_CUSTOM 조회는 호출되지 않음
        assertSame(tofu, matched);
        verify(ingredientRepository, never()).findByIngredientCode(Ingredient.ING_CUSTOM);
    }

    /**
     * 사전에 없는 재료명("엄마표 겉절이")을 입력하더라도 에러가 발생하지 않고
     * 직접 입력용 기본 재료(ING_CUSTOM)로 정상 매핑되는지 검증한다.
     */
    @Test
    void matchReturnsCustomIngredientWhenNotFoundInDictionary() {
        // Given: 사전에 "엄마표 겉절이"가 없고, DB에 ING_CUSTOM 재료가 존재함
        Ingredient customIngredient = Ingredient.builder()
                .ingredientCode(Ingredient.ING_CUSTOM)
                .name("기타")
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByName("엄마표 겉절이")).thenReturn(Optional.empty());
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.of(customIngredient));

        // When
        Ingredient matched = ingredientMatcher.match("엄마표 겉절이");

        // Then: 예외 없이 ING_CUSTOM 엔티티 반환
        assertSame(customIngredient, matched);
        assertEquals(Ingredient.ING_CUSTOM, matched.getIngredientCode());
    }

    /**
     * 사전에 없는 재료명을 입력했는데 DB에 ING_CUSTOM 초기 데이터 행조차 없으면
     * CUSTOM_INGREDIENT_NOT_CONFIGURED(500) 비즈니스 예외를 던지는지 검증한다.
     */
    @Test
    void matchThrowsBusinessExceptionWhenCustomIngredientNotConfigured() {
        // Given: 사전에도 없고 ING_CUSTOM 행도 DB에 없음
        when(ingredientRepository.findByName("엄마표 겉절이")).thenReturn(Optional.empty());
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ingredientMatcher.match("엄마표 겉절이")
        );
        assertEquals(ErrorCode.CUSTOM_INGREDIENT_NOT_CONFIGURED, exception.getErrorCode());
    }
}
