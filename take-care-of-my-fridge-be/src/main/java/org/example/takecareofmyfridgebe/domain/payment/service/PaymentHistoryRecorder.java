package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentHistoryRepository;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 결제 이력을 남기는 곳을 한 곳으로 모은다. 결제 상태 변경과 같은 트랜잭션에서 부르면 함께 저장·롤백된다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryRecorder {

    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public void recordReady(Payment payment) {
        save(payment, PaymentEventType.READY, payment.getAmount(), 0, null, null, PaymentChangedBy.USER, null);
    }

    @Transactional
    public void recordPaid(Payment payment, PaymentChangedBy changedBy) {
        save(payment, PaymentEventType.PAID, payment.getAmount(), 0, null, null, changedBy, null);
    }

    @Transactional
    public void recordFailed(Payment payment, FailReason reason, PaymentChangedBy changedBy) {
        save(payment, PaymentEventType.FAILED, null, 0, null, null, changedBy, reason.getDescription());
    }

    // 받지 않기로 한 결제가 PG에서 결제됐다. 보상 취소가 끝나 CANCELLED가 남기 전까지 재시도 대상이 된다.
    // 웹훅 재전송 등으로 여러 번 불려도 한 번만 남긴다
    @Transactional
    public void recordUnacceptedPaid(Payment payment, PaymentChangedBy changedBy, String reason) {
        if (paymentHistoryRepository.existsByPaymentIdAndEventType(payment.getId(), PaymentEventType.PAID)) {
            return;
        }
        save(payment, PaymentEventType.PAID, payment.getAmount(), 0, null, null, changedBy, reason);
    }

    // 받지 않은 결제의 PG 전액 취소(보상 취소). 이미 남겼으면 다시 남기지 않는다
    @Transactional
    public void recordCompensationCancelled(Payment payment, String pgCancellationId, PaymentChangedBy changedBy,
                                            String reason) {
        if (paymentHistoryRepository.existsByPaymentIdAndEventType(payment.getId(), PaymentEventType.CANCELLED)) {
            return;
        }
        save(payment, PaymentEventType.CANCELLED, payment.getAmount(), payment.getAmount(), null, pgCancellationId,
                changedBy, reason);
    }

    // 웹훅·스케줄러가 넘기는 결제는 영속 상태가 아닐 수 있으므로 ID로 참조만 건다
    private void save(Payment payment, PaymentEventType eventType, Long amount, long cancelledAmount, Long refundId,
                      String pgCancellationId, PaymentChangedBy changedBy, String reason) {
        paymentHistoryRepository.save(PaymentHistory.builder()
                .payment(paymentRepository.getReferenceById(payment.getId()))
                .eventType(eventType)
                .amount(amount)
                .cancelledAmount(cancelledAmount)
                .refundId(refundId)
                .pgCancellationId(pgCancellationId)
                .changedBy(changedBy)
                .reason(reason)
                .build());
    }
}
