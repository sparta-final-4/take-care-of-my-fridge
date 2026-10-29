package org.example.takecareofmyfridgebe.domain.settlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

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
    @AssertTrue(message = "종료일은 시작일과 같거나 늦어야 합니다.")
    public boolean isEndNotBeforeStart() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
