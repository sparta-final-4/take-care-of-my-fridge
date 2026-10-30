package org.example.takecareofmyfridgebe.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

// 결제 한 건에 일어난 일을 시간순으로 쌓는다. 수정하지 않고 추가만 한다.
// payments.status는 READY·PAID·FAILED만 갖고, 환불·보상 취소로 돈이 돌아간 사실은 이력에만 남는다
@Entity
@Table(name = "payment_histories",
        indexes = {
                @Index(name = "idx_payment_histories_payment_id", columnList = "payment_id, created_at"),
                @Index(name = "idx_payment_histories_refund_id", columnList = "refund_id"),
                @Index(name = "idx_payment_histories_created_at", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentHistory extends BaseTimeEntity {

    private static final int REASON_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private PaymentEventType eventType;

    // 이번 사건 금액. 실패는 돈이 움직이지 않아 null
    @Column
    private Long amount;

    // 이 사건 직후 누적 취소 금액
    @Column(name = "cancelled_amount", nullable = false)
    private long cancelledAmount;

    // 환불로 생긴 취소면 환불 ID. 결제가 환불 엔티티에 의존하지 않도록 ID만 둔다
    @Column(name = "refund_id")
    private Long refundId;

    @Column(name = "pg_cancellation_id", length = 200)
    private String pgCancellationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, length = 20)
    private PaymentChangedBy changedBy;

    @Column(length = REASON_MAX_LENGTH)
    private String reason;

    @Builder
    private PaymentHistory(Payment payment, PaymentEventType eventType, Long amount, long cancelledAmount,
                           Long refundId, String pgCancellationId, PaymentChangedBy changedBy, String reason) {
        this.payment = payment;
        this.eventType = eventType;
        this.amount = amount;
        this.cancelledAmount = cancelledAmount;
        this.refundId = refundId;
        this.pgCancellationId = pgCancellationId;
        this.changedBy = changedBy;
        // 사유가 길어 저장에 실패하면 결제 상태 변경까지 되돌려지므로 잘라서 저장한다
        this.reason = reason != null && reason.length() > REASON_MAX_LENGTH
                ? reason.substring(0, REASON_MAX_LENGTH)
                : reason;
    }
}
