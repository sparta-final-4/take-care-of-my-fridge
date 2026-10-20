package org.example.takecareofmyfridgebe.infra.portone.webhook.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOneWebhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WebhookHandlerTest {

    private static final String WEBHOOK_ID = "webhook-1";
    private static final String PORTONE_PAYMENT_ID = "payment-1";
    private static final String BODY = "{}";
    private static final Long EVENT_ID = 50L;
    private static final Long PAYMENT_ID = 10L;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private WebhookEventService webhookEventService;

    @InjectMocks
    private WebhookHandler webhookHandler;

    private PortOneWebhook givenReceived(String type) {
        given(webhookEventService.receive(WEBHOOK_ID, type, BODY)).willReturn(Optional.of(EVENT_ID));
        return new PortOneWebhook(type, PORTONE_PAYMENT_ID);
    }

    private Payment givenPayment() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(1L).totalAmount(10000L).build();
        Payment payment = Payment.builder().order(order).portonePaymentId(PORTONE_PAYMENT_ID).amount(10000L).build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        given(paymentService.findWithOrder(PORTONE_PAYMENT_ID)).willReturn(Optional.of(payment));
        return payment;
    }

    @Test
    @DisplayName("이미 처리가 끝난 웹훅이 다시 오면 아무것도 하지 않는다")
    void handle_duplicate_skipped() {
        given(webhookEventService.receive(WEBHOOK_ID, "Transaction.Paid", BODY)).willReturn(Optional.empty());

        webhookHandler.handle(WEBHOOK_ID, new PortOneWebhook("Transaction.Paid", PORTONE_PAYMENT_ID), BODY);

        verifyNoInteractions(paymentService, paymentFacade);
        verify(webhookEventService, never()).markProcessed(any(), any());
    }

    @Test
    @DisplayName("처리 대상이 아닌 이벤트는 결제를 조회하지 않고 IGNORED로 기록한다")
    void handle_unhandledType_ignored() {
        PortOneWebhook webhook = givenReceived("Transaction.PartialCancelled");

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, null);
        verifyNoInteractions(paymentService, paymentFacade);
    }

    @Test
    @DisplayName("결제를 찾을 수 없으면 IGNORED로 기록한다")
    void handle_paymentNotFound_ignored() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        given(paymentService.findWithOrder(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, null);
        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 상태를 바꿨으면 결제와 함께 PROCESSED로 기록한다")
    void handle_synced_processed() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment)).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markProcessed(EVENT_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("바꿀 상태가 없으면 결제와 함께 IGNORED로 기록한다")
    void handle_notSynced_ignored() {
        PortOneWebhook webhook = givenReceived("Transaction.Cancelled");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment)).willReturn(false);

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("처리 중 오류가 나면 FAILED로 기록하고 예외를 던져 재전송을 받는다")
    void handle_error_markedFailedAndRethrown() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment)).willThrow(new IllegalStateException("PG 오류"));

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, webhook, BODY))
                .isInstanceOf(IllegalStateException.class);

        verify(webhookEventService).markFailed(EVENT_ID, PAYMENT_ID, "PG 오류");
        verify(webhookEventService, never()).markProcessed(any(), any());
    }

    @Test
    @DisplayName("같은 웹훅이 동시에 들어와 다른 요청이 먼저 저장했으면 처리를 건너뛴다")
    void handle_concurrentDuplicate_skipped() {
        given(webhookEventService.receive(WEBHOOK_ID, "Transaction.Paid", BODY))
                .willThrow(new DataIntegrityViolationException("uk_webhook_events_webhook_id"));
        given(webhookEventService.isReceived(WEBHOOK_ID)).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, new PortOneWebhook("Transaction.Paid", PORTONE_PAYMENT_ID), BODY);

        verifyNoInteractions(paymentService, paymentFacade);
    }

    @Test
    @DisplayName("중복이 아닌 제약 위반으로 저장에 실패하면 예외를 다시 던져 재전송을 받는다")
    void handle_otherConstraintViolation_rethrown() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("Data too long for column");
        given(webhookEventService.receive(WEBHOOK_ID, "Transaction.Paid", BODY)).willThrow(violation);
        given(webhookEventService.isReceived(WEBHOOK_ID)).willReturn(false);

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, new PortOneWebhook("Transaction.Paid", PORTONE_PAYMENT_ID), BODY))
                .isSameAs(violation);
        verifyNoInteractions(paymentService, paymentFacade);
    }

    @Test
    @DisplayName("실패 기록이 실패해도 원래 예외를 던지고 기록 오류는 suppressed로 남긴다")
    void handle_markFailedFails_keepsOriginalException() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        IllegalStateException original = new IllegalStateException("PG 오류");
        IllegalStateException recordError = new IllegalStateException("DB 오류");
        given(paymentFacade.syncWithPg(payment)).willThrow(original);
        willThrow(recordError).given(webhookEventService).markFailed(EVENT_ID, PAYMENT_ID, "PG 오류");

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, webhook, BODY))
                .isSameAs(original)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(recordError));
    }
}
