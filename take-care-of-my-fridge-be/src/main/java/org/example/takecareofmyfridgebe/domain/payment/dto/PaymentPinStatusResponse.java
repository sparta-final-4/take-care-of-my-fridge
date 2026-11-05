package org.example.takecareofmyfridgebe.domain.payment.dto;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;

// 결제 비밀번호를 설정했는지, 잠겼는지, 몇 번 더 틀릴 수 있는지
public record PaymentPinStatusResponse(
        boolean isSet,
        boolean isLocked,
        int remainingAttempts
) {
    public static PaymentPinStatusResponse from(PaymentPin paymentPin) {
        return new PaymentPinStatusResponse(true, paymentPin.isLocked(), paymentPin.remainingAttempts());
    }

    public static PaymentPinStatusResponse notSet() {
        return new PaymentPinStatusResponse(false, false, PaymentPin.MAX_FAILED_COUNT);
    }
}
