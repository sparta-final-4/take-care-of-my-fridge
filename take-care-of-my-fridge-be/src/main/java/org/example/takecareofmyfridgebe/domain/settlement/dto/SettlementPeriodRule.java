package org.example.takecareofmyfridgebe.domain.settlement.dto;

import java.time.LocalDate;
import java.time.ZoneId;

// 정산 조회 기간 규칙. 정산 조회와 결제 건별 조회가 같은 규칙을 쓴다. 비어 있는 값은 @NotNull이 따로 막는다
final class SettlementPeriodRule {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // 정산 조회는 처음 볼 때 집계되지 않은 날을 하루씩 집계하므로 기간을 제한한다. 건별 조회도 화면이 같은 기간을 쓰므로 맞춘다
    private static final int MAX_PERIOD_MONTHS = 3;

    private SettlementPeriodRule() {
    }

    static boolean isEndNotBeforeStart(LocalDate startDate, LocalDate endDate) {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    static boolean isEndNotAfterToday(LocalDate endDate) {
        return endDate == null || !endDate.isAfter(LocalDate.now(ZONE));
    }

    // 시작일이 8/1이면 10/31까지 고를 수 있다
    static boolean isWithinMaxPeriod(LocalDate startDate, LocalDate endDate) {
        return startDate == null || endDate == null || endDate.isBefore(startDate.plusMonths(MAX_PERIOD_MONTHS));
    }
}
