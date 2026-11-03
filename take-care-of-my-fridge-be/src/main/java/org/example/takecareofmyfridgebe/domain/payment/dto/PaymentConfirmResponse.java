package org.example.takecareofmyfridgebe.domain.payment.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        Long id,
        Long orderId,
        String portonePaymentId,
        Long amount,
        PaymentStatus status,
        LocalDateTime paidAt,
        PgProvider pgProvider
) {
    public static PaymentConfirmResponse from(Payment payment) {
        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getPortonePaymentId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getPgProvider()
        );
    }
}
