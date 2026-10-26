package org.example.takecareofmyfridgebe.domain.refund.entity;

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
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.time.LocalDateTime;

// 환불 한 건. 신청 즉시 PG 취소를 요청하며 관리자 승인 단계는 없다.
// 한 결제에 부분 환불이 여러 건 생길 수 있다. 상품별 누적 환불 수량이 주문 수량을 넘지 않는지는 환불 신청 로직이 확인한다
@Entity
@Table(name = "refunds",
        indexes = @Index(name = "idx_refunds_refunded_at", columnList = "refunded_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseTimeEntity {

    private static final int FAIL_REASON_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 환불 상품 금액의 합
    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 20)
    private RefundReasonType reasonType;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "portone_cancellation_id", length = 200)
    private String portoneCancellationId;

    @Column(name = "fail_reason", length = FAIL_REASON_MAX_LENGTH)
    private String failReason;

    // 정산 기준 시각. 환불 완료 전에는 null
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Builder
    private Refund(Payment payment, Long refundAmount, RefundReasonType reasonType, String reasonDetail) {
        this.payment = payment;
        this.refundAmount = refundAmount;
        this.reasonType = reasonType;
        this.reasonDetail = reasonDetail;
        this.status = RefundStatus.REQUESTED;
    }

    public void complete(String portoneCancellationId, LocalDateTime refundedAt) {
        validateRequested();
        this.status = RefundStatus.COMPLETED;
        this.portoneCancellationId = portoneCancellationId;
        this.refundedAt = refundedAt;
    }

    // PG 오류 메시지가 컬럼 길이를 넘어 저장에 실패하면 환불이 요청 상태로 남으므로 잘라서 저장한다
    public void fail(String failReason) {
        validateRequested();
        this.status = RefundStatus.FAILED;
        this.failReason = failReason != null && failReason.length() > FAIL_REASON_MAX_LENGTH
                ? failReason.substring(0, FAIL_REASON_MAX_LENGTH)
                : failReason;
    }

    private void validateRequested() {
        if (status != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATE);
        }
    }
}
