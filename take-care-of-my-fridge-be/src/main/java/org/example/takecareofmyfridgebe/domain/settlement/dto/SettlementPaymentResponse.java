package org.example.takecareofmyfridgebe.domain.settlement.dto;

import java.time.LocalDateTime;

// 정산 건별 표의 한 행. 조회 기간 전에 결제하고 기간 안에 환불했으면 paidAmount가 0이다
public record SettlementPaymentResponse(
        Long paymentId,
        LocalDateTime paidAt,
        String orderNumber,
        String buyerName,
        long paidAmount,
        long refundedAmount,
        long netAmount
) {
    public static SettlementPaymentResponse from(PaymentSalesRow row) {
        return new SettlementPaymentResponse(row.paymentId(), row.paidAt(), row.orderNumber(), row.buyerName(),
                row.paidAmount(), row.refundedAmount(), row.paidAmount() - row.refundedAmount());
    }
}
