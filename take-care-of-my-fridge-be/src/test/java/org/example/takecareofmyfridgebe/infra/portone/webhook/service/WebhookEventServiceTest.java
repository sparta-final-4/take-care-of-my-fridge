package org.example.takecareofmyfridgebe.infra.portone.webhook.service;

import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.infra.portone.webhook.entity.WebhookEvent;
import org.example.takecareofmyfridgebe.infra.portone.webhook.entity.WebhookStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.infra.portone.webhook.repository.WebhookEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookEventServiceTest {

    private static final String WEBHOOK_ID = "webhook-1";
    private static final String TYPE = "Transaction.Paid";
    private static final String BODY = "{\"type\":\"Transaction.Paid\"}";
    private static final Long EVENT_ID = 50L;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private WebhookEventService webhookEventService;

    private WebhookEvent newEvent() {
        WebhookEvent event = WebhookEvent.builder().webhookId(WEBHOOK_ID).eventType(TYPE).rawPayload(BODY).build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    @Test
    @DisplayName("처음 받은 웹훅은 RECEIVED로 저장하고 이벤트 ID를 돌려준다")
    void receive_new_saves() {
        given(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).willReturn(Optional.empty());
        given(webhookEventRepository.save(any(WebhookEvent.class))).willAnswer(invocation -> {
            WebhookEvent saved = invocation.getArgument(0);
            assertThat(saved.getProcessStatus()).isEqualTo(WebhookStatus.RECEIVED);
            assertThat(saved.getRawPayload()).isEqualTo(BODY);
            ReflectionTestUtils.setField(saved, "id", EVENT_ID);
            return saved;
        });

        assertThat(webhookEventService.receive(WEBHOOK_ID, TYPE, BODY)).contains(EVENT_ID);
    }

    @Test
    @DisplayName("이미 처리가 끝난 웹훅이면 빈 값을 돌려준다")
    void receive_completed_skips() {
        WebhookEvent event = newEvent();
        event.process(null);
        given(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).willReturn(Optional.of(event));

        assertThat(webhookEventService.receive(WEBHOOK_ID, TYPE, BODY)).isEmpty();
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("이전 처리가 실패한 웹훅은 같은 이벤트로 다시 처리한다")
    void receive_failed_retries() {
        WebhookEvent event = newEvent();
        event.fail(null, "PG 오류");
        given(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).willReturn(Optional.of(event));

        assertThat(webhookEventService.receive(WEBHOOK_ID, TYPE, BODY)).contains(EVENT_ID);
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("처리 완료로 기록하면 결제와 처리 시각이 남는다")
    void markProcessed_setsPaymentAndProcessedAt() {
        WebhookEvent event = newEvent();
        Payment payment = mock(Payment.class);
        given(webhookEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(paymentRepository.getReferenceById(10L)).willReturn(payment);

        webhookEventService.markProcessed(EVENT_ID, 10L);

        assertThat(event.getProcessStatus()).isEqualTo(WebhookStatus.PROCESSED);
        assertThat(event.getPayment()).isSameAs(payment);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패로 기록하면 사유를 500자로 잘라 남긴다")
    void markFailed_truncatesReason() {
        WebhookEvent event = newEvent();
        given(webhookEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        webhookEventService.markFailed(EVENT_ID, null, "가".repeat(600));

        assertThat(event.getProcessStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(event.getFailReason()).hasSize(500);
        assertThat(event.getPayment()).isNull();
        assertThat(event.getProcessedAt()).isNull();
    }
}
