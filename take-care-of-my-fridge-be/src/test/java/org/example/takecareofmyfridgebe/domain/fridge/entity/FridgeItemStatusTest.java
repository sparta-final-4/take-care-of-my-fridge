package org.example.takecareofmyfridgebe.domain.fridge.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FridgeItemStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    /**
     * 소비기한이 어제(D-day -1)인 재료는 EXPIRED로 판별되는지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 어제인 재료는 EXPIRED로 판별한다")
    void returnsExpired_whenExpirationDateIsYesterday() {
        long dDay = FridgeItemStatus.dDayOf(TODAY.minusDays(1), TODAY);

        assertThat(dDay).isEqualTo(-1);
        assertThat(FridgeItemStatus.of(dDay)).isEqualTo(FridgeItemStatus.EXPIRED);
    }

    /**
     * 소비기한이 오늘(D-day 0)인 재료는 IMMINENT로 판별되는지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 오늘인 재료는 IMMINENT로 판별한다")
    void returnsImminent_whenExpirationDateIsToday() {
        long dDay = FridgeItemStatus.dDayOf(TODAY, TODAY);

        assertThat(dDay).isEqualTo(0);
        assertThat(FridgeItemStatus.of(dDay)).isEqualTo(FridgeItemStatus.IMMINENT);
    }

    /**
     * 임박 기준의 마지막 날(D-3)은 IMMINENT로 판별되는지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 3일 남은 재료는 IMMINENT로 판별한다")
    void returnsImminent_whenThreeDaysLeft() {
        long dDay = FridgeItemStatus.dDayOf(TODAY.plusDays(FridgeItemStatus.IMMINENT_DAYS), TODAY);

        assertThat(dDay).isEqualTo(3);
        assertThat(FridgeItemStatus.of(dDay)).isEqualTo(FridgeItemStatus.IMMINENT);
    }

    /**
     * 임박 기준 다음 날(D-4)부터는 NORMAL로 판별되는지 검증한다.
     */
    @Test
    @DisplayName("소비기한이 4일 남은 재료는 NORMAL로 판별한다")
    void returnsNormal_whenFourDaysLeft() {
        long dDay = FridgeItemStatus.dDayOf(TODAY.plusDays(FridgeItemStatus.IMMINENT_DAYS + 1), TODAY);

        assertThat(dDay).isEqualTo(4);
        assertThat(FridgeItemStatus.of(dDay)).isEqualTo(FridgeItemStatus.NORMAL);
    }
}
