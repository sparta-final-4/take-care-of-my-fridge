package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentConfirmRequest(
        @NotNull(message = "주문 ID는 필수입니다")
        Long orderId,
        
        @NotBlank(message = "PortOne 결제 ID는 필수입니다.")
        String portonePaymentId
) {
}
