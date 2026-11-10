package org.example.takecareofmyfridgebe.domain.webhook.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.facade.BillingKeyFacade;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.domain.refund.facade.RefundFacade;
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
    private RefundFacade refundFacade;

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private BillingKeyFacade billingKeyFacade;

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
    void handle_skip_whenAlreadyProcessed() {
        given(webhookEventService.receive(WEBHOOK_ID, "Transaction.Paid", BODY)).willReturn(Optional.empty());

        webhookHandler.handle(WEBHOOK_ID, new PortOneWebhook("Transaction.Paid", PORTONE_PAYMENT_ID), BODY);

        verifyNoInteractions(paymentService, paymentFacade);
        verify(webhookEventService, never()).markProcessed(any(), any());
    }

    @Test
    @DisplayName("처리 대상이 아닌 이벤트는 결제를 조회하지 않고 IGNORED로 기록한다")
    void handle_markIgnored_whenUnhandledType() {
        PortOneWebhook webhook = givenReceived("Transaction.Ready");

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, null);
        verifyNoInteractions(paymentService, paymentFacade, refundFacade);
    }

    @Test
    @DisplayName("부분 취소 웹훅은 환불을 PG와 맞추고, 바뀐 것이 있으면 PROCESSED로 기록한다")
    void handle_syncRefund_whenPartialCancelled() {
        PortOneWebhook webhook = givenReceived("Transaction.PartialCancelled");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).willReturn(false);
        given(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markProcessed(EVENT_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("결제 완료 웹훅은 환불 쪽을 부르지 않는다")
    void handle_skipRefundSync_whenPaid() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verifyNoInteractions(refundFacade);
    }

    @Test
    @DisplayName("결제를 찾을 수 없으면 IGNORED로 기록한다")
    void handle_markIgnored_whenPaymentNotFound() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        given(paymentService.findWithOrder(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, null);
        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 상태를 바꿨으면 결제와 함께 PROCESSED로 기록한다")
    void handle_markProcessed_whenPaymentSynced() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markProcessed(EVENT_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("바꿀 상태가 없으면 결제와 함께 IGNORED로 기록한다")
    void handle_markIgnored_whenNothingSynced() {
        PortOneWebhook webhook = givenReceived("Transaction.Cancelled");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).willReturn(false);
        given(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).willReturn(false);

        webhookHandler.handle(WEBHOOK_ID, webhook, BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("처리 중 오류가 나면 FAILED로 기록하고 예외를 던져 재전송을 받는다")
    void handle_markFailedAndThrowException_whenSyncFails() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        given(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).willThrow(new IllegalStateException("PG 오류"));

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, webhook, BODY))
                .isInstanceOf(IllegalStateException.class);

        verify(webhookEventService).markFailed(EVENT_ID, PAYMENT_ID, "PG 오류");
        verify(webhookEventService, never()).markProcessed(any(), any());
    }

    @Test
    @DisplayName("같은 웹훅이 동시에 들어와 다른 요청이 먼저 저장했으면 처리를 건너뛴다")
    void handle_skip_whenConcurrentDuplicate() {
        given(webhookEventService.receive(WEBHOOK_ID, "Transaction.Paid", BODY))
                .willThrow(new DataIntegrityViolationException("uk_webhook_events_webhook_id"));
        given(webhookEventService.isReceived(WEBHOOK_ID)).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, new PortOneWebhook("Transaction.Paid", PORTONE_PAYMENT_ID), BODY);

        verifyNoInteractions(paymentService, paymentFacade);
    }

    @Test
    @DisplayName("중복이 아닌 제약 위반으로 저장에 실패하면 예외를 다시 던져 재전송을 받는다")
    void handle_throwException_whenOtherConstraintViolation() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("Data too long for column");
        given(webhookEventService.receive(WEBHOOK_ID, "Transaction.Paid", BODY)).willThrow(violation);
        given(webhookEventService.isReceived(WEBHOOK_ID)).willReturn(false);

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, new PortOneWebhook("Transaction.Paid", PORTONE_PAYMENT_ID), BODY))
                .isSameAs(violation);
        verifyNoInteractions(paymentService, paymentFacade);
    }

    @Test
    @DisplayName("실패 기록이 실패해도 원래 예외를 던지고 기록 오류는 suppressed로 남긴다")
    void handle_throwOriginalException_whenMarkFailedFails() {
        PortOneWebhook webhook = givenReceived("Transaction.Paid");
        Payment payment = givenPayment();
        IllegalStateException original = new IllegalStateException("PG 오류");
        IllegalStateException recordError = new IllegalStateException("DB 오류");
        given(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).willThrow(original);
        willThrow(recordError).given(webhookEventService).markFailed(EVENT_ID, PAYMENT_ID, "PG 오류");

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, webhook, BODY))
                .isSameAs(original)
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(recordError));
    }

    private PortOneWebhook givenBillingKeyIssued() {
        given(webhookEventService.receive(WEBHOOK_ID, "BillingKey.Issued", BODY)).willReturn(Optional.of(EVENT_ID));
        return new PortOneWebhook("BillingKey.Issued", null, "billing-key-1");
    }

    @Test
    @DisplayName("빌링키 발급 웹훅으로 카드를 저장하면 처리 완료로 기록하고 결제는 찾지 않는다")
    void handle_markProcessed_whenBillingKeyRegistered() {
        given(billingKeyFacade.registerFromWebhook("billing-key-1")).willReturn(true);

        webhookHandler.handle(WEBHOOK_ID, givenBillingKeyIssued(), BODY);

        verify(webhookEventService).markProcessed(EVENT_ID, null);
        verifyNoInteractions(paymentService, paymentFacade, refundFacade);
    }

    @Test
    @DisplayName("빌링키 발급 웹훅이 저장 대상이 아니면 무시로 기록한다")
    void handle_markIgnored_whenBillingKeyNotRegistered() {
        given(billingKeyFacade.registerFromWebhook("billing-key-1")).willReturn(false);

        webhookHandler.handle(WEBHOOK_ID, givenBillingKeyIssued(), BODY);

        verify(webhookEventService).markIgnored(EVENT_ID, null);
    }

    @Test
    @DisplayName("빌링키 발급 웹훅 처리가 실패하면 실패로 기록하고 예외를 다시 던져 재전송을 받는다")
    void handle_markFailedAndThrowException_whenBillingKeyRegisterFails() {
        willThrow(new RuntimeException("PortOne 조회 실패")).given(billingKeyFacade).registerFromWebhook("billing-key-1");

        assertThatThrownBy(() -> webhookHandler.handle(WEBHOOK_ID, givenBillingKeyIssued(), BODY))
                .isInstanceOf(RuntimeException.class);
        verify(webhookEventService).markFailed(EVENT_ID, null, "PortOne 조회 실패");
    }
}
