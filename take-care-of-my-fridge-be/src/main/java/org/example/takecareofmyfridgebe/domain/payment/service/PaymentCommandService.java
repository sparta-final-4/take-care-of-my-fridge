package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult.Rejection;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// 결제 상태를 바꾸고, 승인 시 주문을 결제 완료로 바꾼다. 외부 PG 호출은 하지 않는다.
// 상태를 바꾼 트랜잭션 안에서 결제 이력도 남긴다.
// 한 주문에 결제가 여러 건이므로 주문 → 결제 순서로 잠가 같은 주문의 요청을 하나씩 처리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private static final String PORTONE_PAYMENT_ID_PREFIX = "payment-";
    private static final long PAYMENT_EXPIRE_MINUTES = 10;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final EntityManager entityManager;
    private final PaymentHistoryRecorder paymentHistoryRecorder;

    // 주문에 새 결제를 만든다. 금액은 클라이언트 값이 아닌 주문 금액을 사용한다.
    // 한 주문의 결제 대기 건은 최대 한 건이다. 호출 전에 이전 결제 대기 건을 정리하므로, 여기서 남아 있으면
    // 동시에 들어온 다른 요청이 방금 만든 것이다. 만료됐을 수도 있는 결제를 재사용하지 않고 거절한다
    @Transactional
    public Payment create(Long orderId) {
        Order order = lockOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        if (!paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.READY).isEmpty()) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        Payment payment = Payment.builder()
                .order(order)
                // KG이니시스는 주문번호(oid)를 40자까지만 받으므로 UUID의 하이픈을 빼 8 + 32 = 40자로 만든다
                .portonePaymentId(PORTONE_PAYMENT_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""))
                .amount(order.getTotalAmount())
                .expiresAt(LocalDateTime.now().plusMinutes(PAYMENT_EXPIRE_MINUTES))
                .build();
        paymentRepository.save(payment);
        paymentHistoryRecorder.recordReady(payment);
        return payment;
    }

    // 승인하지 못한 결제는 PG에서 돈이 빠진 상태이므로, 호출한 쪽이 거절 결과를 보고 PG 결제를 취소한다
    @Transactional
    public PaymentApproveResult approve(Payment target, PaymentChangedBy changedBy) {
        Payment payment = getForUpdate(target);

        // 확정 요청과 웹훅 중 먼저 처리한 쪽이 있으면 같은 결과를 돌려준다
        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentApproveResult.approved(PaymentConfirmResponse.from(payment));
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentApproveResult.rejected(Rejection.PAYMENT_ALREADY_FAILED);
        }
        // 같은 주문의 다른 결제가 먼저 결제 완료됐거나 주문이 취소·만료됐다
        if (payment.getOrder().getStatus() != OrderStatus.PENDING) {
            log.warn("결제할 수 없는 주문의 결제 완료: portonePaymentId={}, 주문 상태={}",
                    payment.getPortonePaymentId(), payment.getOrder().getStatus());
            payment.fail(FailReason.ORDER_NOT_PAYABLE);
            paymentHistoryRecorder.recordFailed(payment, FailReason.ORDER_NOT_PAYABLE, changedBy);
            return PaymentApproveResult.rejected(Rejection.ORDER_NOT_PAYABLE);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        payment.complete(paidAt);
        payment.getOrder().markPaid(paidAt);
        paymentHistoryRecorder.recordPaid(payment, changedBy);
        return PaymentApproveResult.approved(PaymentConfirmResponse.from(payment));
    }

    // 결제 시도 하나만 실패 처리한다. 주문은 PENDING으로 남아 다시 결제할 수 있고, 주문 취소·재고 복구는 주문 쪽이 맡는다.
    // 실제로 실패 처리했으면 true, 다른 요청이 이미 처리해 그대로 뒀으면 false
    @Transactional
    public boolean fail(Payment target, FailReason reason, PaymentChangedBy changedBy) {
        Payment payment = getForUpdate(target);

        if (payment.getStatus() != PaymentStatus.READY) {
            return false;
        }

        payment.fail(reason);
        paymentHistoryRecorder.recordFailed(payment, reason, changedBy);
        return true;
    }

    // 결제가 속한 주문은 바뀌지 않으므로 호출한 쪽이 읽어 둔 주문 ID로 주문부터 잠근다.
    // 주문 ID를 얻으려고 잠금 전에 일반 조회를 하면 그 시점 스냅샷으로 refresh하게 되므로, 잠금이 트랜잭션의 첫 조회여야 한다
    private Payment getForUpdate(Payment target) {
        lockOrder(target.getOrder().getId());
        Payment payment = paymentRepository.findWithOrderByPortonePaymentIdForUpdate(target.getPortonePaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        entityManager.refresh(payment);
        return payment;
    }

    // OSIV로 같은 요청에서 먼저 읽은 엔티티가 재사용되면 잠금 후에도 예전 상태가 남으므로 DB 값으로 다시 읽는다.
    // 아직 반영하지 않은 변경이 덮어써지지 않도록 먼저 flush한다
    private Order lockOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        entityManager.flush();
        entityManager.refresh(order);
        return order;
    }
}
