package org.example.takecareofmyfridgebe.domain.settlement.dto;

import java.time.LocalDate;

// 정산 표의 한 행이자 차트의 막대 하나. 일 단위면 시작일과 종료일이 같다.
// 화면에 보일 기간 글자(2026.10.07 (수), 10.13 ~ 10.19, 2026.10)는 프론트가 두 날짜로 만든다
public record SettlementPeriodResponse(
        LocalDate startDate,
        LocalDate endDate,
        long paidAmount,
        long refundedAmount,
        long netAmount,
        int paidCount,
        int refundedCount
) {
    public static SettlementPeriodResponse of(LocalDate startDate, LocalDate endDate, SalesAmountResponse sales) {
        return new SettlementPeriodResponse(startDate, endDate, sales.paidAmount(), sales.refundedAmount(),
                sales.netAmount(), sales.paidCount(), sales.refundedCount());
    }
}
