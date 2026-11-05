package org.example.takecareofmyfridgebe.domain.settlement.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;

import java.time.LocalDateTime;

// 결제 이력을 결제 한 건씩 묶은 조회 결과. 금액은 조회 기간 안에서 일어난 결제·환불만 센다.
// buyerName은 회원 닉네임이며, 회원 행이 없으면 null이다. registeredCard는 등록 카드 결제 여부다
public record PaymentSalesRow(
        Long paymentId,
        LocalDateTime paidAt,
        String orderNumber,
        String buyerName,
        boolean registeredCard,
        PgProvider pgProvider,
        long paidAmount,
        long refundedAmount
) {
}
