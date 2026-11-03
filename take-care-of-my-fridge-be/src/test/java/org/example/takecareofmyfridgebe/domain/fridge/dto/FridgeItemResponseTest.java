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

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("소비기한이 지난 재료는 EXPIRED 상태와 음수 dDay로 변환한다")
    void returnsExpired_whenExpirationDateHasPassed() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.minusDays(2)), TODAY);

        assertThat(response.dDay()).isEqualTo(-2);
        assertThat(response.status()).isEqualTo(FridgeItemStatus.EXPIRED);
        assertThat(response.expired()).isTrue();
        assertThat(response.imminent()).isFalse();
    }

    /**
     * 소비기한이 오늘인 재료는 dDay가 0, status가 IMMINENT인지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 오늘인 재료는 IMMINENT 상태와 dDay 0으로 변환한다")
    void returnsImminent_whenExpirationDateIsToday() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY), TODAY);

        assertThat(response.dDay()).isEqualTo(0);
        assertThat(response.status()).isEqualTo(FridgeItemStatus.IMMINENT);
        assertThat(response.expired()).isFalse();
        assertThat(response.imminent()).isTrue();
    }

    /**
     * 소비기한이 3일 이내인 재료는 dDay가 양수, status가 IMMINENT인지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 3일 이내인 재료는 IMMINENT 상태로 변환한다")
    void returnsImminent_whenExpirationDateIsWithinThreeDays() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.plusDays(3)), TODAY);

        assertThat(response.dDay()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(FridgeItemStatus.IMMINENT);
        assertThat(response.expired()).isFalse();
        assertThat(response.imminent()).isTrue();
    }

    /**
     * 소비기한이 4일 이상 남은 재료는 dDay가 양수, status가 NORMAL인지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 4일 이상 남은 재료는 NORMAL 상태로 변환한다")
    void returnsNormal_whenFourOrMoreDaysLeft() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.plusDays(4)), TODAY);

        assertThat(response.dDay()).isEqualTo(4);
        assertThat(response.status()).isEqualTo(FridgeItemStatus.NORMAL);
        assertThat(response.expired()).isFalse();
        assertThat(response.imminent()).isFalse();
    }

    @Test
    @DisplayName("표준 사전에 매핑된 일반 재료는 ingredientType이 NORMAL이다")
    void returnsNormalType_whenIngredientIsMapped() {
        Ingredient normal = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(normal), TODAY);
        assertThat(response.ingredientType()).isEqualTo(IngredientType.NORMAL);
    }

    @Test
    @DisplayName("사용자 직접 입력 재료(ING_CUSTOM)는 ingredientType이 CUSTOM이다")
    void returnsCustomType_whenIngredientIsCustom() {
        Ingredient custom = Ingredient.builder()
                .ingredientCode(Ingredient.ING_CUSTOM)
                .name("직접입력")
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(custom), TODAY);
        assertThat(response.ingredientType()).isEqualTo(IngredientType.CUSTOM);
    }

    @Test
    @DisplayName("밀키트 재료(ING_MEALKIT)는 ingredientType이 MEALKIT이다")
    void returnsMealkitType_whenIngredientIsMealkit() {
        Ingredient mealkit = Ingredient.builder()
                .ingredientCode(Ingredient.ING_MEALKIT)
                .name("밀키트")
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(mealkit), TODAY);
        assertThat(response.ingredientType()).isEqualTo(IngredientType.MEALKIT);
    }

    @Test
    @DisplayName("기본 양념 재료(is_basic_seasoning = true)는 basicSeasoning이 true다")
    void returnsBasicSeasoningTrue_whenIngredientIsBasicSeasoning() {
        Ingredient soySauce = Ingredient.builder()
                .ingredientCode("ING_SOY_SAUCE")
                .name("간장")
                .basicSeasoning(true)
                .build();

        FridgeItemResponse response = FridgeItemResponse.from(fridgeWithIngredient(soySauce), TODAY);
        assertThat(response.basicSeasoning()).isTrue();
        assertThat(response.ingredientType()).isEqualTo(IngredientType.NORMAL);
    }

    @Test
    @DisplayName("기본 양념이 아닌 재료와 재료가 없는 냉장고 재료는 basicSeasoning이 false다")
    void returnsBasicSeasoningFalse_whenNotBasicSeasoningOrIngredientIsMissing() {
        Ingredient tofu = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .basicSeasoning(false)
                .build();

        assertThat(FridgeItemResponse.from(fridgeWithIngredient(tofu), TODAY).basicSeasoning()).isFalse();
        assertThat(FridgeItemResponse.from(fridgeExpiringOn(TODAY), TODAY).basicSeasoning()).isFalse();
    }
}
