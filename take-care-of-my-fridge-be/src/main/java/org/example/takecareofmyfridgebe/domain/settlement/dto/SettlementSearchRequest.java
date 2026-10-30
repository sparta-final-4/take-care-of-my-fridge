package org.example.takecareofmyfridgebe.domain.settlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.ZoneId;

// 시작일과 종료일을 모두 포함한다
public record SettlementSearchRequest(
        @NotNull(message = "조회 단위를 선택해 주세요.")
        SalesPeriodUnit unit,

        @NotNull(message = "시작일을 입력해 주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @NotNull(message = "종료일을 입력해 주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate
) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // 처음 조회할 때 집계되지 않은 날을 하루씩 집계하므로 한 번에 볼 수 있는 기간을 제한한다
    private static final int MAX_PERIOD_MONTHS = 3;

    @AssertTrue(message = "종료일은 시작일과 같거나 늦어야 합니다.")
    public boolean isEndNotBeforeStart() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "종료일은 오늘까지 선택할 수 있습니다.")
    public boolean isEndNotAfterToday() {
        return endDate == null || !endDate.isAfter(LocalDate.now(ZONE));
    }

    // 시작일이 8/1이면 10/31까지 고를 수 있다
    @AssertTrue(message = "조회 기간은 최대 3개월입니다.")
    public boolean isWithinMaxPeriod() {
        return startDate == null || endDate == null || endDate.isBefore(startDate.plusMonths(MAX_PERIOD_MONTHS));
    }
}
