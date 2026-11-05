package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;

// 결제 비밀번호 처음 설정
public record PaymentPinSetRequest(
        @NotNull(message = "결제 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "결제 비밀번호는 숫자 6자리입니다.")
        String pin
) {
    // 원래 값은 저장하지 않고 서비스가 만든 해시만 넣는다
    public PaymentPin toEntity(Long memberId, String pinHash) {
        return PaymentPin.builder()
                .memberId(memberId)
                .pinHash(pinHash)
                .build();
    }
}
