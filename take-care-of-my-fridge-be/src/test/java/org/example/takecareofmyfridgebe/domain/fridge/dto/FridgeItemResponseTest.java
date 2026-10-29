package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FridgeItemResponseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private static Fridge fridgeExpiringOn(LocalDate expirationDate) {
        return Fridge.builder()
                .memberId(7L)
                .rawName("두부")
                .quantity(BigDecimal.ONE)
                .storage(StorageType.REFRIGERATED)
                .expirationDate(expirationDate)
                .build();
    }

    private static Fridge fridgeWithIngredient(Ingredient ingredient) {
        return Fridge.builder()
                .memberId(7L)
                .ingredient(ingredient)
                .rawName("테스트 재료")
                .quantity(BigDecimal.ONE)
                .storage(StorageType.REFRIGERATED)
                .expirationDate(TODAY.plusDays(7))
                .build();
    }

    /**
     * 소비기한이 지난 재료는 dDay가 음수, status가 EXPIRED이고
     * 호환용 필드(expired, imminent)도 status에서 만들어지는지 검증한다.
     */
    @Test
    void fromWithPastExpirationDateReturnsExpired() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.minusDays(2)), TODAY);

        assertEquals(-2, response.dDay());
        assertEquals(FridgeItemStatus.EXPIRED, response.status());
        assertTrue(response.expired());
        assertFalse(response.imminent());
    }

    /**
     * 소비기한이 오늘인 재료는 dDay가 0, status가 IMMINENT인지 검증한다.
     */
    @Test
    void fromWithTodayExpirationDateReturnsImminent() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY), TODAY);

        assertEquals(0, response.dDay());
        assertEquals(FridgeItemStatus.IMMINENT, response.status());
        assertFalse(response.expired());
        assertTrue(response.imminent());
    }

    /**
     * 소비기한이 3일 이내인 재료는 dDay가 양수, status가 IMMINENT인지 검증한다.
     */
    @Test
    void fromWithNearExpirationDateReturnsImminent() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.plusDays(3)), TODAY);

        assertEquals(3, response.dDay());
        assertEquals(FridgeItemStatus.IMMINENT, response.status());
        assertFalse(response.expired());
        assertTrue(response.imminent());
    }

    /**
     * 소비기한이 4일 이상 남은 재료는 dDay가 양수, status가 NORMAL인지 검증한다.
     */
    @Test
    void fromWithFarExpirationDateReturnsNormal() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.plusDays(4)), TODAY);

        assertEquals(4, response.dDay());
        assertEquals(FridgeItemStatus.NORMAL, response.status());
        assertFalse(response.expired());
        assertFalse(response.imminent());
    }

    @Test
    @DisplayName("표준 사전에 매핑된 일반 재료는 ingredientType이 NORMAL이다")
    void fromWithNormalIngredientReturnsNormalType() {
        Ingredient normal = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(normal), TODAY);
        assertEquals(IngredientType.NORMAL, response.ingredientType());
    }

    @Test
    @DisplayName("사용자 직접 입력 재료(ING_CUSTOM)는 ingredientType이 CUSTOM이다")
    void fromWithCustomIngredientReturnsCustomType() {
        Ingredient custom = Ingredient.builder()
                .ingredientCode(Ingredient.ING_CUSTOM)
                .name("직접입력")
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(custom), TODAY);
        assertEquals(IngredientType.CUSTOM, response.ingredientType());
    }

    @Test
    @DisplayName("밀키트 재료(ING_MEALKIT)는 ingredientType이 MEALKIT이다")
    void fromWithMealkitIngredientReturnsMealkitType() {
        Ingredient mealkit = Ingredient.builder()
                .ingredientCode(Ingredient.ING_MEALKIT)
                .name("밀키트")
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(mealkit), TODAY);
        assertEquals(IngredientType.MEALKIT, response.ingredientType());
    }
}
