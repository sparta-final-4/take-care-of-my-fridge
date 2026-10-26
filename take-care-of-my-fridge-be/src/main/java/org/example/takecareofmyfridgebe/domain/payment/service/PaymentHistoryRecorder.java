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
