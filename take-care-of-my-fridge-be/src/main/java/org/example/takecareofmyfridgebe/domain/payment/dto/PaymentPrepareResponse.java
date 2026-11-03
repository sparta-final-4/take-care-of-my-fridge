package org.example.takecareofmyfridgebe.domain.payment.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentCheckout;

import java.time.LocalDateTime;

// 프론트가 이 값으로 PortOne 결제창을 띄운다. easyPayProvider는 간편결제가 아니면 null
public record PaymentPrepareResponse(
        Long orderId,
        String portonePaymentId,
        String orderName,
        Long amount,
        LocalDateTime expiresAt,
        PgProvider pgProvider,
        String channelKey,
        String payMethod,
        String easyPayProvider
) {
    public static PaymentPrepareResponse of(Payment payment, String orderName, PaymentCheckout checkout) {
        return new PaymentPrepareResponse(
                payment.getOrder().getId(),
                payment.getPortonePaymentId(),
                orderName,
                payment.getAmount(),
                payment.getExpiresAt(),
                payment.getPgProvider(),
                checkout.channelKey(),
                checkout.payMethod(),
                checkout.easyPayProvider()
        );
    }
}
