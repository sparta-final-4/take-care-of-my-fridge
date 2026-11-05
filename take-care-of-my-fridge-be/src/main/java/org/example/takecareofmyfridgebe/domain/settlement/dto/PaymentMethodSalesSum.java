package org.example.takecareofmyfridgebe.domain.settlement.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;

// 결제 이력으로 센 결제 수단별 기간 합계. 같은 조건으로 세므로 모든 수단을 더하면 DailySalesSum과 같다.
// 등록 카드 결제(registeredCard)는 결제창으로 낸 같은 결제대행사 결제와 따로 센다
public record PaymentMethodSalesSum(
        boolean registeredCard,
        PgProvider pgProvider,
        long paidAmount,
        long paidCount,
        long refundedAmount,
        long refundedCount
) {
}
