package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
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
        assertTrue(response.imminent());
        assertFalse(response.expired());
    }

    /**
     * 임박 기준을 넘긴 재료(D-4)는 status가 NORMAL이고 호환용 필드가 모두 false인지 검증한다.
     */
    @Test
    void fromWithLaterExpirationDateReturnsNormal() {
        FridgeItemResponse response = FridgeItemResponse.from(fridgeExpiringOn(TODAY.plusDays(4)), TODAY);

        assertEquals(4, response.dDay());
        assertEquals(FridgeItemStatus.NORMAL, response.status());
        assertFalse(response.imminent());
        assertFalse(response.expired());
    }
}
