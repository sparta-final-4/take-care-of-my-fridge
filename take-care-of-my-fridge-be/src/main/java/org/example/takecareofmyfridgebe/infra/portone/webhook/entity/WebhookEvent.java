package org.example.takecareofmyfridgebe.infra.portone.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.time.LocalDateTime;

// 서명 검증을 통과한 PortOne 웹훅만 기록한다
@Entity
@Table(name = "webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent extends BaseTimeEntity {

    private static final int FAIL_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 결제 매칭 실패·처리 대상이 아닌 웹훅은 NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "webhook_id", nullable = false, unique = true, length = 200)
    private String webhookId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", nullable = false, length = 20)
    private WebhookStatus processStatus;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "fail_reason", length = FAIL_REASON_MAX_LENGTH)
    private String failReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private WebhookEvent(String webhookId, String eventType, String rawPayload) {
        this.webhookId = webhookId;
        this.eventType = eventType;
        this.rawPayload = rawPayload;
        this.processStatus = WebhookStatus.RECEIVED;
    }

    // 같은 웹훅이 다시 와도 이미 끝난 처리는 반복하지 않는다
    public boolean isCompleted() {
        return processStatus == WebhookStatus.PROCESSED || processStatus == WebhookStatus.IGNORED;
    }

    public void process(Payment payment) {
        complete(WebhookStatus.PROCESSED, payment);
    }

    public void ignore(Payment payment) {
        complete(WebhookStatus.IGNORED, payment);
    }

    public void fail(Payment payment, String reason) {
        this.processStatus = WebhookStatus.FAILED;
        this.payment = payment;
        this.failReason = reason != null && reason.length() > FAIL_REASON_MAX_LENGTH
                ? reason.substring(0, FAIL_REASON_MAX_LENGTH)
                : reason;
    }

    private void complete(WebhookStatus status, Payment payment) {
        this.processStatus = status;
        this.payment = payment;
        this.processedAt = LocalDateTime.now();
    }
}
