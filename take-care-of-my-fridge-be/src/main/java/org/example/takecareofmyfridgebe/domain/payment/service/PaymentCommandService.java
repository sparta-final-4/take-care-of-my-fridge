package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 결제와 주문 상태를 한 트랜잭션에서 함께 바꾼다. 외부 PG 호출은 하지 않는다.
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentConfirmResponse approve(String portonePaymentId) {
        Payment payment = getForUpdate(portonePaymentId);

        // 확정 요청과 웹훅 중 먼저 처리한 쪽이 있으면 같은 결과를 돌려준다
        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentConfirmResponse.from(payment);
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        payment.complete(paidAt);
        payment.getOrder().markPaid(paidAt);
        return PaymentConfirmResponse.from(payment);
    }

    // 실제로 실패 처리했으면 true, 다른 요청이 이미 처리해 그대로 뒀으면 false
    @Transactional
    public boolean fail(String portonePaymentId, FailReason reason) {
        Payment payment = getForUpdate(portonePaymentId);

        if (payment.getStatus() != PaymentStatus.READY) {
            return false;
        }

        payment.fail(reason);
        payment.getOrder().cancel();
        // TODO: 주문 로직에 재고 복구, 번개 드롭 1인 1개 기록 해제 메서드
        return true;
    }

    private Payment getForUpdate(String portonePaymentId) {
        return paymentRepository.findWithOrderByPortonePaymentIdForUpdate(portonePaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
