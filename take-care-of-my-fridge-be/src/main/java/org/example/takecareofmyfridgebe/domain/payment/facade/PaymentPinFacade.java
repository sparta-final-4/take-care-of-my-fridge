package org.example.takecareofmyfridgebe.domain.payment.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinResetRequest;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinResetLimiter;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinService;
import org.springframework.stereotype.Component;

// 결제 비밀번호 재설정: 시도 횟수 제한(Redis) → 로그인 비밀번호 확인 → 재설정(DB 트랜잭션)
// Redis 호출을 DB 트랜잭션과 묶지 않으려고 서비스 밖에서 조율한다
@Component
@RequiredArgsConstructor
public class PaymentPinFacade {

    private final PaymentPinService paymentPinService;
    private final PaymentPinResetLimiter paymentPinResetLimiter;

    public void reset(Long memberId, PaymentPinResetRequest request) {
        // 쉬운 비밀번호는 로그인 비밀번호를 맞혀 보는 시도가 아니므로 횟수에 넣지 않는다
        PaymentPin.validateStrength(request.newPin());
        paymentPinResetLimiter.acquire(memberId);
        paymentPinService.verifyLoginPassword(memberId, request.loginPassword());
        // 로그인 비밀번호를 맞혔으니 바로 지운다. 뒤의 저장이 실패해 다시 시도해도 맞힌 시도가 틀린 횟수로 쌓이지 않는다
        paymentPinResetLimiter.clear(memberId);
        paymentPinService.reset(memberId, request);
    }
}
