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
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
        indexes = @Index(name = "idx_payments_status_expires_at", columnList = "status, expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "portone_payment_id", nullable = false, unique = true, length = 100)
    private String portonePaymentId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 결제 대기 만료 시각. 이 시각이 지나 PG에서 결제가 완료되면 받지 않고 취소한다
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", length = 30)
    private FailReason failReason;

    @Builder
    private Payment(Order order, String portonePaymentId, Long amount, LocalDateTime expiresAt) {
        this.order = order;
        this.portonePaymentId = portonePaymentId;
        this.amount = amount;
        this.expiresAt = expiresAt;
        this.status = PaymentStatus.READY;
    }

    // PG가 기록한 결제 완료 시각이 만료 시각을 넘었는지. 만료 시각과 같으면 기한 내로 본다
    public boolean isPaidAfterExpiry(LocalDateTime pgPaidAt) {
        return pgPaidAt.isAfter(expiresAt);
    }

    public void complete(LocalDateTime paidAt) {
        validateReady();
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt;
    }

    public void fail(FailReason reason) {
        validateReady();
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }

    private void validateReady() {
        if (status != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}
