package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 등록 카드로 결제할 때 고른 카드와 결제 비밀번호. 금액은 요청으로 받지 않고 주문 금액을 쓴다
public record BillingKeyPaymentRequest(
        @NotNull(message = "결제할 카드는 필수입니다.")
        Long cardId,

        @NotNull(message = "결제 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "결제 비밀번호는 숫자 6자리입니다.")
        String pin
) {
}
