package org.example.takecareofmyfridgebe.domain.payment.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;

import java.time.LocalDateTime;

// 프론트가 이 값으로 PortOne 결제창을 띄운다
public record PaymentPrepareResponse(
        Long orderId,
        String portonePaymentId,
        String orderName,
        Long amount,
        LocalDateTime expiresAt
) {
    public static PaymentPrepareResponse of(Payment payment, String orderName) {
        return new PaymentPrepareResponse(
                payment.getOrder().getId(),
                payment.getPortonePaymentId(),
                orderName,
                payment.getAmount(),
                payment.getExpiresAt()
        );
    }
}
