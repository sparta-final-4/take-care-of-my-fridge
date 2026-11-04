package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotNull;

// 등록 카드로 결제할 때 고른 카드. 금액은 요청으로 받지 않고 주문 금액을 쓴다
public record BillingKeyPaymentRequest(
        @NotNull(message = "결제할 카드는 필수입니다.")
        Long cardId
) {
}
