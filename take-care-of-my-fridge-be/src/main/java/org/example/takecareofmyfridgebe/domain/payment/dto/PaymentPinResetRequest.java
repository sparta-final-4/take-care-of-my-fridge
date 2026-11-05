package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;

// 결제 비밀번호를 잊었거나 잠겼을 때 로그인 비밀번호로 확인하고 다시 설정한다
public record PaymentPinResetRequest(
        @NotBlank(message = "로그인 비밀번호는 필수입니다.")
        String loginPassword,

        @NotNull(message = "새 결제 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "결제 비밀번호는 숫자 6자리입니다.")
        String newPin
) {
    // 아직 설정하지 않은 회원이 재설정하면 새로 만든다. 원래 값은 저장하지 않고 해시만 넣는다
    public PaymentPin toEntity(Long memberId, String pinHash) {
        return PaymentPin.builder()
                .memberId(memberId)
                .pinHash(pinHash)
                .build();
    }
}
