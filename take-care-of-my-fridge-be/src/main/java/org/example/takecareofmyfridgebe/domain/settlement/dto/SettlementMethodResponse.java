package org.example.takecareofmyfridgebe.domain.settlement.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;

// 정산 결제 수단별 표의 한 행. 등록 카드 결제면 registeredCard가 true이고 pgProvider는 실제로 승인한 결제대행사다.
// 환불은 원래 결제의 수단으로 센다
public record SettlementMethodResponse(
        boolean registeredCard,
        PgProvider pgProvider,
        long paidAmount,
        long refundedAmount,
        long netAmount,
        int paidCount,
        int refundedCount
) {
    public static SettlementMethodResponse from(PaymentMethodSalesSum sum) {
        SalesAmountResponse sales = SalesAmountResponse.of(sum.paidAmount(), sum.refundedAmount(),
                Math.toIntExact(sum.paidCount()), Math.toIntExact(sum.refundedCount()));
        return new SettlementMethodResponse(sum.registeredCard(), sum.pgProvider(), sales.paidAmount(),
                sales.refundedAmount(), sales.netAmount(), sales.paidCount(), sales.refundedCount());
    }
}
