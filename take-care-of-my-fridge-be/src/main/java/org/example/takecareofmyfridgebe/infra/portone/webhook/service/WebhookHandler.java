package org.example.takecareofmyfridgebe.infra.portone.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneWebhook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

// 서명 검증을 통과한 웹훅을 기록하고 결제 상태 동기화를 PaymentFacade에 맡긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookHandler {

    // TODO: 환불 도메인 구현 후 Transaction.PartialCancelled 처리 추가
    private static final Set<String> HANDLED_TYPES =
            Set.of("Transaction.Paid", "Transaction.Failed", "Transaction.Cancelled");

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;
    private final WebhookEventService webhookEventService;

    public void handle(String webhookId, PortOneWebhook webhook, String rawPayload) {
        Optional<Long> eventId;
        try {
            eventId = webhookEventService.receive(webhookId, webhook.type(), rawPayload);
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 같은 웹훅을 먼저 저장한 경우만 건너뛴다. 그쪽이 실패하면 PortOne이 다시 보낸다
            // 그 외 제약 위반은 다시 던져 오류로 응답하고 재전송을 받는다
            if (!webhookEventService.isReceived(webhookId)) {
                throw e;
            }
            log.info("[Webhook] 동시 수신된 웹훅 건너뜀: webhookId={}", webhookId);
            return;
        }
        if (eventId.isEmpty()) {
            log.info("[Webhook] 이미 처리된 웹훅 재수신: webhookId={}", webhookId);
            return;
        }

        Long paymentId = null;
        try {
            if (!HANDLED_TYPES.contains(webhook.type())) {
                webhookEventService.markIgnored(eventId.get(), null);
                return;
            }

            Optional<Payment> payment = paymentService.findWithOrder(webhook.paymentId());
            if (payment.isEmpty()) {
                log.warn("[Webhook] 결제 매칭 실패: webhookId={}, portonePaymentId={}", webhookId, webhook.paymentId());
                webhookEventService.markIgnored(eventId.get(), null);
                return;
            }

            paymentId = payment.get().getId();
            if (paymentFacade.syncWithPg(payment.get(), PaymentChangedBy.WEBHOOK)) {
                webhookEventService.markProcessed(eventId.get(), paymentId);
            } else {
                webhookEventService.markIgnored(eventId.get(), paymentId);
            }
        } catch (RuntimeException e) {
            // 실패를 기록하고 오류로 응답해 PortOne이 재전송하게 한다
            log.error("[Webhook] 처리 실패: webhookId={}", webhookId, e);
            recordFailure(eventId.get(), paymentId, e);
            throw e;
        }
    }

    // 실패 기록 자체가 실패해도 원래 예외가 묻히지 않도록 suppressed로 붙인다
    private void recordFailure(Long eventId, Long paymentId, RuntimeException cause) {
        try {
            webhookEventService.markFailed(eventId, paymentId, cause.getMessage());
        } catch (RuntimeException recordError) {
            cause.addSuppressed(recordError);
        }
    }
}
