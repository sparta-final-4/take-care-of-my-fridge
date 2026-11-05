package org.example.takecareofmyfridgebe.domain.settlement.dto;

import java.util.List;

// 정산 화면 한 번 조회의 응답. summary는 조회 기간 전체 합계(카드·합계 행), periods는 단위별 행(표·차트),
// methods는 조회 기간의 결제 수단별 합계(결제금액이 큰 순)
public record SettlementResponse(
        SalesAmountResponse summary,
        List<SettlementPeriodResponse> periods,
        List<SettlementMethodResponse> methods
) {
}
