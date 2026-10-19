package org.example.takecareofmyfridgebe.domain.fridge.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FridgeItemStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    /**
     * 소비기한이 어제(D-day -1)인 재료는 EXPIRED로 판별되는지 검증한다.
     */
    @Test
    void ofYesterdayReturnsExpired() {
        long dDay = FridgeItemStatus.dDayOf(TODAY.minusDays(1), TODAY);

        assertEquals(-1, dDay);
        assertEquals(FridgeItemStatus.EXPIRED, FridgeItemStatus.of(dDay));
    }

    /**
     * 소비기한이 오늘(D-day 0)인 재료는 IMMINENT로 판별되는지 검증한다.
     */
    @Test
    void ofTodayReturnsImminent() {
        long dDay = FridgeItemStatus.dDayOf(TODAY, TODAY);

        assertEquals(0, dDay);
        assertEquals(FridgeItemStatus.IMMINENT, FridgeItemStatus.of(dDay));
    }

    /**
     * 임박 기준의 마지막 날(D-3)은 IMMINENT로 판별되는지 검증한다.
     */
    @Test
    void ofLastImminentDayReturnsImminent() {
        long dDay = FridgeItemStatus.dDayOf(TODAY.plusDays(FridgeItemStatus.IMMINENT_DAYS), TODAY);

        assertEquals(3, dDay);
        assertEquals(FridgeItemStatus.IMMINENT, FridgeItemStatus.of(dDay));
    }

    /**
     * 임박 기준 다음 날(D-4)부터는 NORMAL로 판별되는지 검증한다.
     */
    @Test
    void ofDayAfterImminentReturnsNormal() {
        long dDay = FridgeItemStatus.dDayOf(TODAY.plusDays(FridgeItemStatus.IMMINENT_DAYS + 1), TODAY);

        assertEquals(4, dDay);
        assertEquals(FridgeItemStatus.NORMAL, FridgeItemStatus.of(dDay));
    }
}
