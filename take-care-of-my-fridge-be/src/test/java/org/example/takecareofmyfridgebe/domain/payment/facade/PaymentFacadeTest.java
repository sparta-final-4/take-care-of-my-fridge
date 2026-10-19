package org.example.takecareofmyfridgebe.domain.payment.facade;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentCommandService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.portone.client.PortOneClient;
import org.example.takecareofmyfridgebe.infra.portone.dto.PortOnePaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long ORDER_ID = 100L;
    private static final Long OTHER_ORDER_ID = 200L;
    private static final String PORTONE_PAYMENT_ID = "payment-1";
    private static final long AMOUNT = 10000L;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private PortOneClient portOneClient;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private Payment newPayment() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(MEMBER_ID).totalAmount(AMOUNT).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        Payment payment = Payment.builder().order(order).portonePaymentId(PORTONE_PAYMENT_ID).amount(AMOUNT).build();
        given(paymentService.getWithOrder(PORTONE_PAYMENT_ID)).willReturn(payment);
        return payment;
    }

    private void givenPgPayment(String status, long amount) {
        given(portOneClient.getPayment(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePaymentResponse(status, new PortOnePaymentResponse.Amount(amount))));
    }

    private PaymentConfirmResponse confirm(Long memberId) {
        return paymentFacade.confirmPayment(memberId, new PaymentConfirmRequest(ORDER_ID, PORTONE_PAYMENT_ID));
    }

    // confirmPayment

    @Test
    @DisplayName("PG 결제 완료 + 금액 일치면 승인한다")
    void confirm_success() {
        newPayment();
        givenPgPayment("PAID", AMOUNT);
        PaymentConfirmResponse approved = mock(PaymentConfirmResponse.class);
        given(paymentCommandService.approve(PORTONE_PAYMENT_ID)).willReturn(approved);

        PaymentConfirmResponse response = confirm(MEMBER_ID);

        assertThat(response).isSameAs(approved);
        verify(paymentCommandService, never()).fail(anyString(), any());
        verify(portOneClient, never()).cancelPayment(anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("다른 회원의 결제면 ORDER_ACCESS_DENIED, PG를 조회하지 않는다")
    void confirm_otherMember_denied() {
        newPayment();

        assertThatThrownBy(() -> confirm(OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(portOneClient, paymentCommandService);
    }

    @Test
    @DisplayName("요청의 주문 ID가 결제의 주문과 다르면 INVALID_INPUT_VALUE, PG를 조회하지 않는다")
    void confirm_orderIdMismatch_rejected() {
        newPayment();

        assertThatThrownBy(() -> paymentFacade.confirmPayment(MEMBER_ID,
                new PaymentConfirmRequest(OTHER_ORDER_ID, PORTONE_PAYMENT_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(portOneClient, paymentCommandService);
    }

    @Test
    @DisplayName("이미 PAID면 PG 조회 없이 같은 결과를 돌려준다")
    void confirm_alreadyPaid_returnsSameResult() {
        Payment payment = newPayment();
        payment.complete(LocalDateTime.of(2026, 10, 19, 10, 0));

        PaymentConfirmResponse response = confirm(MEMBER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(portOneClient, paymentCommandService);
    }

    @Test
    @DisplayName("이미 FAILED면 ALREADY_PROCESSED_PAYMENT")
    void confirm_alreadyFailed_throws() {
        Payment payment = newPayment();
        payment.fail(FailReason.TIMEOUT);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        verifyNoInteractions(portOneClient, paymentCommandService);
    }

    @Test
    @DisplayName("PG에 결제가 없으면 PAYMENT_NOT_PAID, 상태는 바꾸지 않는다")
    void confirm_pgPaymentMissing_throws() {
        newPayment();
        given(portOneClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("PG가 FAILED면 PG_FAILED로 실패 처리하고 PAYMENT_NOT_PAID")
    void confirm_pgFailed_failsPayment() {
        newPayment();
        givenPgPayment("FAILED", AMOUNT);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verify(paymentCommandService).fail(PORTONE_PAYMENT_ID, FailReason.PG_FAILED);
        verify(paymentCommandService, never()).approve(anyString());
    }

    @Test
    @DisplayName("PG 결제가 아직 진행 중이면 상태를 바꾸지 않고 PAYMENT_NOT_PAID")
    void confirm_pgInProgress_keepsReady() {
        newPayment();
        givenPgPayment("READY", AMOUNT);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("금액이 다르면 AMOUNT_MISMATCH로 실패 처리한 뒤 PG 결제를 취소한다")
    void confirm_amountMismatch_failsThenCancelsPg() {
        newPayment();
        givenPgPayment("PAID", 100L);
        given(paymentCommandService.fail(PORTONE_PAYMENT_ID, FailReason.AMOUNT_MISMATCH)).willReturn(true);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        InOrder inOrder = inOrder(paymentCommandService, portOneClient);
        inOrder.verify(paymentCommandService).fail(PORTONE_PAYMENT_ID, FailReason.AMOUNT_MISMATCH);
        inOrder.verify(portOneClient).cancelPayment(anyString(), anyString(), isNull());
        verify(paymentCommandService, never()).approve(anyString());
    }

    @Test
    @DisplayName("금액 불일치인데 다른 요청이 이미 처리했으면 PG 결제를 취소하지 않는다")
    void confirm_amountMismatch_alreadyProcessed_skipsPgCancel() {
        newPayment();
        givenPgPayment("PAID", 100L);
        given(paymentCommandService.fail(PORTONE_PAYMENT_ID, FailReason.AMOUNT_MISMATCH)).willReturn(false);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(portOneClient, never()).cancelPayment(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("금액 불일치 시 PG 취소가 실패해도 실패 처리는 남긴다")
    void confirm_amountMismatch_pgCancelFails_stillFails() {
        newPayment();
        givenPgPayment("PAID", 100L);
        given(paymentCommandService.fail(PORTONE_PAYMENT_ID, FailReason.AMOUNT_MISMATCH)).willReturn(true);
        willThrow(new IllegalStateException("PG 오류")).given(portOneClient).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(paymentCommandService).fail(PORTONE_PAYMENT_ID, FailReason.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("PG 응답에 금액이 없으면 상태를 바꾸지 않고 PAYMENT_NOT_PAID")
    void confirm_pgAmountMissing_keepsReady() {
        newPayment();
        given(portOneClient.getPayment(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(new PortOnePaymentResponse("PAID", null)));

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verifyNoInteractions(paymentCommandService);
        verify(portOneClient, never()).cancelPayment(anyString(), anyString(), any());
    }

    // cancelByUser

    @Test
    @DisplayName("결제창을 닫았고 PG에 결제가 없으면 USER_CANCELLED로 실패 처리한다")
    void cancelByUser_noPgPayment_fails() {
        newPayment();
        given(portOneClient.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        paymentFacade.cancelByUser(MEMBER_ID, PORTONE_PAYMENT_ID);

        verify(paymentCommandService).fail(PORTONE_PAYMENT_ID, FailReason.USER_CANCELLED);
    }

    @Test
    @DisplayName("PG에서 이미 결제가 끝났으면 취소하지 않는다")
    void cancelByUser_pgPaid_throws() {
        newPayment();
        givenPgPayment("PAID", AMOUNT);

        assertThatThrownBy(() -> paymentFacade.cancelByUser(MEMBER_ID, PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("READY가 아닌 결제는 취소하지 않는다")
    void cancelByUser_notReady_throws() {
        Payment payment = newPayment();
        payment.complete(LocalDateTime.of(2026, 10, 19, 10, 0));

        assertThatThrownBy(() -> paymentFacade.cancelByUser(MEMBER_ID, PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        verifyNoInteractions(portOneClient, paymentCommandService);
    }

    @Test
    @DisplayName("다른 회원의 결제는 취소할 수 없다")
    void cancelByUser_otherMember_denied() {
        newPayment();

        assertThatThrownBy(() -> paymentFacade.cancelByUser(OTHER_MEMBER_ID, PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(portOneClient, paymentCommandService);
    }
}
