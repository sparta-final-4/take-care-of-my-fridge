package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 결제 비밀번호 변경. 현재 비밀번호가 맞아야 바꾼다
public record PaymentPinChangeRequest(
        @NotNull(message = "현재 결제 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "결제 비밀번호는 숫자 6자리입니다.")
        String currentPin,

        @NotNull(message = "새 결제 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "결제 비밀번호는 숫자 6자리입니다.")
        String newPin
) {
}
