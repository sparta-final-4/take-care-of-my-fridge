package org.example.takecareofmyfridgebe.domain.settlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// 정산 건별 조회. 기간 규칙은 정산 조회와 같고, 시작일과 종료일을 모두 포함한다
public record SettlementPaymentSearchRequest(
        @NotNull(message = "시작일을 입력해 주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @NotNull(message = "종료일을 입력해 주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
        @Max(value = 10000, message = "페이지는 10000 이하여야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        Integer size
) {
    public SettlementPaymentSearchRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    @AssertTrue(message = "종료일은 시작일과 같거나 늦어야 합니다.")
    public boolean isEndNotBeforeStart() {
        return SettlementPeriodRule.isEndNotBeforeStart(startDate, endDate);
    }

    @AssertTrue(message = "종료일은 오늘까지 선택할 수 있습니다.")
    public boolean isEndNotAfterToday() {
        return SettlementPeriodRule.isEndNotAfterToday(endDate);
    }

    @AssertTrue(message = "조회 기간은 최대 3개월입니다.")
    public boolean isWithinMaxPeriod() {
        return SettlementPeriodRule.isWithinMaxPeriod(startDate, endDate);
    }
}
