package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

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
        // TODO: 주문 생성에서 재고 차감을 구현하면 여기서 재고를 복구하고 번개 드롭 1인 1개 기록(OrderItem.releaseFlashDropMember)을 해제한다.
        //  confirm·결제창 닫기·웹훅·만료 스케줄러의 실패 처리가 모두 이 메서드를 거치므로 이곳 한 군데에서 처리한다
        return true;
    }

    private Payment getForUpdate(String portonePaymentId) {
        Payment payment = paymentRepository.findWithOrderByPortonePaymentIdForUpdate(portonePaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        // OSIV로 같은 요청에서 먼저 읽은 엔티티가 재사용되면 잠금 후에도 예전 상태가 남으므로 DB 값으로 다시 읽는다.
        // 아직 반영하지 않은 변경이 덮어써지지 않도록 먼저 flush한다
        entityManager.flush();
        entityManager.refresh(payment);
        entityManager.refresh(payment.getOrder());
        return payment;
    }
}
