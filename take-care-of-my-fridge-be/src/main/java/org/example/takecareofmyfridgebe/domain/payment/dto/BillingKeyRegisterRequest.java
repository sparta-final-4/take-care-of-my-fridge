package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

// 카드 등록 창이 성공으로 끝나면 받은 빌링키. 서버가 PortOne에서 다시 확인한 뒤 저장한다
public record BillingKeyRegisterRequest(
        @NotBlank(message = "빌링키는 필수입니다.")
        String billingKey
) {
}
