package org.example.takecareofmyfridgebe.domain.fridge.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 냉장고 재료의 소비기한 상태.
 * DB에 저장하지 않고 조회 시점의 D-day로 판별하며, 상태 판별 규칙은 이 enum 한 곳에만 둔다.
 * 상수 선언 순서가 곧 판별 순서다. of()는 위에서부터 처음 일치하는 상수를 반환하므로 순서를 바꾸지 않는다.
 */
public enum FridgeItemStatus {

    // 소비기한이 지남 (D-day 음수)
    EXPIRED {
        @Override
        boolean matches(long dDay) {
            return dDay < 0;
        }
    },

    // 오늘부터 D-IMMINENT_DAYS 이내 (EXPIRED가 먼저 걸러지므로 0 이상만 들어온다)
    IMMINENT {
        @Override
        boolean matches(long dDay) {
            return dDay <= IMMINENT_DAYS;
        }
    },

    // 여유 있음
    NORMAL {
        @Override
        boolean matches(long dDay) {
            return true;
        }
    };

    // 소비기한 임박 기준일(D-3)
    public static final int IMMINENT_DAYS = 3;

    abstract boolean matches(long dDay);

    public static FridgeItemStatus of(long dDay) {
        for (FridgeItemStatus status : values()) {
            if (status.matches(dDay)) {
                return status;
            }
        }
        throw new IllegalStateException("판별되지 않은 D-day: " + dDay);
    }

    // 오늘 기준 소비기한까지 남은 일수 (오늘이면 0, 지났으면 음수)
    public static long dDayOf(LocalDate expirationDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expirationDate);
    }
}
