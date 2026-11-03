package org.example.takecareofmyfridgebe.domain.payment.port;

// 프론트가 PG 결제창을 열 때 쓰는 값. easyPayProvider는 간편결제가 아니면 null
public record PaymentCheckout(
        String channelKey,
        String payMethod,
        String easyPayProvider
) {
}
