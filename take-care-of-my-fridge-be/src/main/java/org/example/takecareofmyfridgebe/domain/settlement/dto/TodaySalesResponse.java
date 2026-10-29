package org.example.takecareofmyfridgebe.domain.settlement.dto;

import java.time.LocalDate;

// 아직 집계 전인 오늘(한국 시간) 매출. daily_sales가 아니라 결제·환불 기록으로 조회 시점까지 계산한다
public record TodaySalesResponse(
        LocalDate salesDate,
        SalesAmountResponse sales
) {
}
