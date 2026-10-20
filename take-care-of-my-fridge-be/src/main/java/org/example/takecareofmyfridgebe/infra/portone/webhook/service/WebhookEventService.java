package org.example.takecareofmyfridgebe.infra.portone.webhook.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.infra.portone.webhook.entity.WebhookEvent;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.infra.portone.webhook.repository.WebhookEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 결제 처리가 실패해도 기록은 남도록 결제 상태 변경과 다른 트랜잭션에서 저장한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;

    // 처리할 웹훅이면 이벤트 ID를, 이미 처리가 끝난 중복 수신이면 빈 값을 돌려준다
    @Transactional
    public Optional<Long> receive(String webhookId, String eventType, String rawPayload) {
        Optional<WebhookEvent> existing = webhookEventRepository.findByWebhookId(webhookId);
        if (existing.isPresent()) {
            // RECEIVED·FAILED는 이전 처리가 끝나지 않은 것이므로 다시 처리한다
            return existing.get().isCompleted() ? Optional.empty() : Optional.of(existing.get().getId());
        }

        WebhookEvent event = WebhookEvent.builder()
                .webhookId(webhookId)
                .eventType(eventType)
                .rawPayload(rawPayload)
                .build();
        return Optional.of(webhookEventRepository.save(event).getId());
    }

    // 동시 수신으로 저장이 충돌했을 때, 다른 요청이 같은 웹훅을 이미 저장했는지 확인한다
    public boolean isReceived(String webhookId) {
        return webhookEventRepository.existsByWebhookId(webhookId);
    }

    @Transactional
    public void markProcessed(Long eventId, Long paymentId) {
        getEvent(eventId).process(getPaymentReference(paymentId));
    }

    @Transactional
    public void markIgnored(Long eventId, Long paymentId) {
        getEvent(eventId).ignore(getPaymentReference(paymentId));
    }

    @Transactional
    public void markFailed(Long eventId, Long paymentId, String reason) {
        getEvent(eventId).fail(getPaymentReference(paymentId), reason);
    }

    private WebhookEvent getEvent(Long eventId) {
        return webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WEBHOOK_EVENT_NOT_FOUND));
    }

    private Payment getPaymentReference(Long paymentId) {
        return paymentId == null ? null : paymentRepository.getReferenceById(paymentId);
    }
}
