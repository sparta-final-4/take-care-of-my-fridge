package org.example.takecareofmyfridgebe.domain.settlement.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;

import java.time.LocalDateTime;

// 정산 건별 표의 한 행. 조회 기간 전에 결제하고 기간 안에 환불했으면 paidAmount가 0이다.
// 결제 수단은 등록 카드 결제 여부(registeredCard)와 실제로 승인한 결제대행사(pgProvider)다
public record SettlementPaymentResponse(
        Long paymentId,
        LocalDateTime paidAt,
        String orderNumber,
        String buyerName,
        boolean registeredCard,
        PgProvider pgProvider,
        long paidAmount,
        long refundedAmount,
        long netAmount
) {
    public static SettlementPaymentResponse from(PaymentSalesRow row) {
        return new SettlementPaymentResponse(row.paymentId(), row.paidAt(), row.orderNumber(), row.buyerName(),
                row.registeredCard(), row.pgProvider(), row.paidAmount(), row.refundedAmount(),
                row.paidAmount() - row.refundedAmount());
    }
}
