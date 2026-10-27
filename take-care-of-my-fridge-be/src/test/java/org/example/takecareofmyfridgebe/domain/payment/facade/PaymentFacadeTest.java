package org.example.takecareofmyfridgebe.domain.payment.facade;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentCommandService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 20, 10, 10);

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentHistoryRecorder paymentHistoryRecorder;

    @InjectMocks
    private PaymentFacade paymentFacade;

    // 테스트마다 결제는 하나뿐이므로 PortOne 결제 ID로 같은 결제인지 확인한다
    private static Payment thePayment() {
        return argThat(payment -> payment != null && PORTONE_PAYMENT_ID.equals(payment.getPortonePaymentId()));
    }

    private Payment newPayment() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(MEMBER_ID).totalAmount(AMOUNT).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        Payment payment = Payment.builder().order(order).portonePaymentId(PORTONE_PAYMENT_ID).amount(AMOUNT)
                .expiresAt(EXPIRES_AT).build();
        given(paymentService.getWithOrder(PORTONE_PAYMENT_ID)).willReturn(payment);
        return payment;
    }

    // PAID면 만료 1분 전에 결제가 끝난 것으로 둔다
    private void givenPgPayment(String status, long amount) {
        LocalDateTime paidAt = "PAID".equals(status) ? EXPIRES_AT.minusMinutes(1) : null;
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(new PaymentGatewayResponse(PORTONE_PAYMENT_ID, status, amount, 0L, paidAt)));
    }

    private void givenPgPaidAt(LocalDateTime paidAt) {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(new PaymentGatewayResponse(PORTONE_PAYMENT_ID, "PAID", AMOUNT, 0L, paidAt)));
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
        given(paymentCommandService.approve(thePayment(), any())).willReturn(PaymentApproveResult.approved(approved));

        PaymentConfirmResponse response = confirm(MEMBER_ID);

        assertThat(response).isSameAs(approved);
        verify(paymentCommandService, never()).fail(any(), any(), any());
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("다른 회원의 결제면 ORDER_ACCESS_DENIED, PG를 조회하지 않는다")
    void confirm_otherMember_denied() {
        newPayment();

        assertThatThrownBy(() -> confirm(OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    @Test
    @DisplayName("요청의 주문 ID가 결제의 주문과 다르면 INVALID_INPUT_VALUE, PG를 조회하지 않는다")
    void confirm_orderIdMismatch_rejected() {
        newPayment();

        assertThatThrownBy(() -> paymentFacade.confirmPayment(MEMBER_ID,
                new PaymentConfirmRequest(OTHER_ORDER_ID, PORTONE_PAYMENT_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    @Test
    @DisplayName("이미 PAID면 PG 조회 없이 같은 결과를 돌려준다")
    void confirm_alreadyPaid_returnsSameResult() {
        Payment payment = newPayment();
        payment.complete(LocalDateTime.of(2026, 10, 19, 10, 0));

        PaymentConfirmResponse response = confirm(MEMBER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    @ParameterizedTest(name = "{0}이면 {1}")
    @CsvSource({
            "TIMEOUT, PAYMENT_EXPIRED",
            "AMOUNT_MISMATCH, PAYMENT_AMOUNT_MISMATCH",
            "ORDER_NOT_PAYABLE, PAYMENT_ORDER_NOT_PAYABLE",
            "PG_FAILED, ALREADY_PROCESSED_PAYMENT",
            "USER_CANCELLED, ALREADY_PROCESSED_PAYMENT",
            "SUPERSEDED, ALREADY_PROCESSED_PAYMENT"
    })
    @DisplayName("이미 FAILED면 PG 조회 없이 실패 이유에 맞는 오류로 거절한다(웹훅·스케줄러가 먼저 실패 처리한 경우)")
    void confirm_alreadyFailed_throwsByFailReason(FailReason failReason, ErrorCode expected) {
        Payment payment = newPayment();
        payment.fail(failReason);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    @Test
    @DisplayName("PG에 결제가 없으면 PAYMENT_NOT_PAID, 상태는 바꾸지 않는다")
    void confirm_pgPaymentMissing_throws() {
        newPayment();
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

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
        verify(paymentCommandService).fail(thePayment(), eq(FailReason.PG_FAILED), any());
        verify(paymentCommandService, never()).approve(any(), any());
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
        given(paymentCommandService.fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any())).willReturn(true);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        InOrder inOrder = inOrder(paymentCommandService, paymentGateway);
        inOrder.verify(paymentCommandService).fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any());
        inOrder.verify(paymentGateway).cancelPayment(anyString(), anyString(), isNull());
        verify(paymentCommandService, never()).approve(any(), any());
    }

    @Test
    @DisplayName("금액 불일치인데 다른 요청이 이미 처리했으면 PG 결제를 취소하지 않는다")
    void confirm_amountMismatch_alreadyProcessed_skipsPgCancel() {
        newPayment();
        givenPgPayment("PAID", 100L);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any())).willReturn(false);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("금액 불일치 시 PG 취소가 실패해도 실패 처리는 남긴다")
    void confirm_amountMismatch_pgCancelFails_stillFails() {
        newPayment();
        givenPgPayment("PAID", 100L);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any())).willReturn(true);
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(paymentCommandService).fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any());
    }

    @Test
    @DisplayName("PG 응답에 금액이 없으면 상태를 바꾸지 않고 PAYMENT_NOT_PAID")
    void confirm_pgAmountMissing_keepsReady() {
        newPayment();
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(new PaymentGatewayResponse(PORTONE_PAYMENT_ID, "PAID", null, 0L, EXPIRES_AT.minusMinutes(1))));

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verifyNoInteractions(paymentCommandService);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
    }

    // failByUser

    @Test
    @DisplayName("결제창을 닫았고 PG에 결제가 없으면 USER_CANCELLED로 실패 처리한다")
    void failByUser_noPgPayment_fails() {
        newPayment();
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        paymentFacade.failByUser(MEMBER_ID, PORTONE_PAYMENT_ID);

        verify(paymentCommandService).fail(thePayment(), eq(FailReason.USER_CANCELLED), any());
    }

    @Test
    @DisplayName("PG에서 이미 결제가 끝났으면 실패 처리하지 않는다")
    void failByUser_pgPaid_throws() {
        newPayment();
        givenPgPayment("PAID", AMOUNT);

        assertThatThrownBy(() -> paymentFacade.failByUser(MEMBER_ID, PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("READY가 아닌 결제는 실패 처리하지 않는다")
    void failByUser_notReady_throws() {
        Payment payment = newPayment();
        payment.complete(LocalDateTime.of(2026, 10, 19, 10, 0));

        assertThatThrownBy(() -> paymentFacade.failByUser(MEMBER_ID, PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    @Test
    @DisplayName("다른 회원의 결제는 실패 처리할 수 없다")
    void failByUser_otherMember_denied() {
        newPayment();

        assertThatThrownBy(() -> paymentFacade.failByUser(OTHER_MEMBER_ID, PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    // syncWithPg

    private Payment syncPayment() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(MEMBER_ID).totalAmount(AMOUNT).build();
        return Payment.builder().order(order).portonePaymentId(PORTONE_PAYMENT_ID).amount(AMOUNT)
                .expiresAt(EXPIRES_AT).build();
    }

    @Test
    @DisplayName("PG에 결제가 없으면 아무것도 바꾸지 않는다")
    void sync_pgPaymentMissing_noChange() {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isFalse();
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("PG PAID + 우리 READY + 금액 일치면 승인한다")
    void sync_pgPaid_ready_approves() {
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any())).willReturn(PaymentApproveResult.approved(mock(PaymentConfirmResponse.class)));

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isTrue();
        verify(paymentCommandService).approve(thePayment(), any());
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PG PAID + 우리 READY + 금액 불일치면 실패 처리 후 PG를 취소한다")
    void sync_pgPaid_ready_amountMismatch_failsAndCancels() {
        givenPgPayment("PAID", AMOUNT + 1);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any())).willReturn(true);

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isTrue();
        InOrder inOrder = inOrder(paymentCommandService, paymentGateway);
        inOrder.verify(paymentCommandService).fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any());
        inOrder.verify(paymentGateway).cancelPayment(anyString(), anyString(), isNull());
        verify(paymentCommandService, never()).approve(any(), any());
    }

    @Test
    @DisplayName("PG PAID + 우리 READY + 금액 불일치인데 다른 요청이 먼저 실패 처리했어도 PG를 취소한다")
    void sync_pgPaid_ready_amountMismatch_alreadyFailed_stillCancels() {
        givenPgPayment("PAID", AMOUNT + 1);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any())).willReturn(false);

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isTrue();
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
    }

    @Test
    @DisplayName("웹훅에서 금액 불일치 PG 취소가 실패하면 예외를 던져 재전송으로 다시 시도한다")
    void sync_amountMismatch_pgCancelFails_throws() {
        givenPgPayment("PAID", AMOUNT + 1);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.AMOUNT_MISMATCH), any())).willReturn(true);
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PG PAID + 우리 FAILED면 PG 결제를 보상 취소한다")
    void sync_pgPaid_failed_compensates() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("PAID", AMOUNT);

        assertThat(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).isTrue();
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("보상 취소가 실패하면 예외를 그대로 던져 웹훅 재전송으로 다시 시도한다")
    void sync_compensationFails_throws() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("PAID", AMOUNT);
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PG PAID + 우리 PAID면 아무것도 바꾸지 않는다")
    void sync_pgPaid_paid_noChange() {
        Payment payment = syncPayment();
        payment.complete(LocalDateTime.of(2026, 10, 19, 10, 0));
        givenPgPayment("PAID", AMOUNT);

        assertThat(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).isFalse();
        verifyNoInteractions(paymentCommandService);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PG FAILED + 우리 READY면 PG_FAILED로 실패 처리한다")
    void sync_pgFailed_ready_fails() {
        givenPgPayment("FAILED", AMOUNT);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.PG_FAILED), any())).willReturn(true);

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isTrue();
        verify(paymentCommandService).fail(thePayment(), eq(FailReason.PG_FAILED), any());
    }

    @Test
    @DisplayName("PG CANCELLED + 우리 PAID면 환불 도메인 몫이므로 바꾸지 않는다")
    void sync_pgCancelled_paid_noChange() {
        Payment payment = syncPayment();
        payment.complete(LocalDateTime.of(2026, 10, 19, 10, 0));
        givenPgPayment("CANCELLED", AMOUNT);

        assertThat(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).isFalse();
        verifyNoInteractions(paymentCommandService);
    }

    // 만료 시각 판단

    @Test
    @DisplayName("confirm: 만료 시각 이후 결제 완료면 TIMEOUT 실패 처리 후 PG를 취소하고 PAYMENT_EXPIRED")
    void confirm_paidAfterExpiry_failsAndCancels() {
        newPayment();
        givenPgPaidAt(EXPIRES_AT.plusSeconds(1));
        given(paymentCommandService.fail(thePayment(), eq(FailReason.TIMEOUT), any())).willReturn(true);

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_EXPIRED);
        InOrder inOrder = inOrder(paymentCommandService, paymentGateway);
        inOrder.verify(paymentCommandService).fail(thePayment(), eq(FailReason.TIMEOUT), any());
        inOrder.verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
        verify(paymentCommandService, never()).approve(any(), any());
    }

    @Test
    @DisplayName("confirm: 만료 시각과 같은 시각에 결제 완료면 기한 내로 보고 승인한다")
    void confirm_paidAtExpiry_approves() {
        newPayment();
        givenPgPaidAt(EXPIRES_AT);
        given(paymentCommandService.approve(thePayment(), any())).willReturn(PaymentApproveResult.approved(mock(PaymentConfirmResponse.class)));

        confirm(MEMBER_ID);

        verify(paymentCommandService).approve(thePayment(), any());
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("confirm: 만료 후 결제인데 PG 취소가 실패해도 PAYMENT_EXPIRED로 응답한다")
    void confirm_paidAfterExpiry_pgCancelFails_stillExpired() {
        newPayment();
        givenPgPaidAt(EXPIRES_AT.plusMinutes(1));
        given(paymentCommandService.fail(thePayment(), eq(FailReason.TIMEOUT), any())).willReturn(true);
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_EXPIRED);
    }

    @Test
    @DisplayName("confirm: PG 결제 완료 시각이 없으면 상태를 바꾸지 않고 PAYMENT_NOT_PAID")
    void confirm_pgPaidAtMissing_keepsReady() {
        newPayment();
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(new PaymentGatewayResponse(PORTONE_PAYMENT_ID, "PAID", AMOUNT, 0L, null)));

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verifyNoInteractions(paymentCommandService);
    }

    @Test
    @DisplayName("웹훅: READY + 만료 시각 이후 결제 완료면 TIMEOUT 실패 처리 후 PG를 취소한다")
    void sync_paidAfterExpiry_failsAndCancels() {
        givenPgPaidAt(EXPIRES_AT.plusSeconds(1));

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isTrue();
        InOrder inOrder = inOrder(paymentCommandService, paymentGateway);
        inOrder.verify(paymentCommandService).fail(thePayment(), eq(FailReason.TIMEOUT), any());
        inOrder.verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
        verify(paymentCommandService, never()).approve(any(), any());
    }

    @Test
    @DisplayName("웹훅: 만료 후 결제의 PG 취소가 실패하면 예외를 던져 재전송으로 다시 시도한다")
    void sync_paidAfterExpiry_pgCancelFails_throws() {
        givenPgPaidAt(EXPIRES_AT.plusSeconds(1));
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isInstanceOf(IllegalStateException.class);
    }

    // expire (스케줄러)

    @Test
    @DisplayName("만료: PG에 결제가 없으면 TIMEOUT으로 실패 처리한다")
    void expire_noPgPayment_timesOut() {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        paymentFacade.expire(syncPayment());

        verify(paymentCommandService).fail(thePayment(), eq(FailReason.TIMEOUT), any());
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("만료: PG 결제가 아직 진행 중이면 TIMEOUT으로 실패 처리한다")
    void expire_pgInProgress_timesOut() {
        givenPgPayment("READY", AMOUNT);

        paymentFacade.expire(syncPayment());

        verify(paymentCommandService).fail(thePayment(), eq(FailReason.TIMEOUT), any());
    }

    @Test
    @DisplayName("만료: PG가 실패로 확정했으면 PG_FAILED로만 실패 처리한다")
    void expire_pgFailed_failsWithPgFailed() {
        givenPgPayment("FAILED", AMOUNT);
        given(paymentCommandService.fail(thePayment(), eq(FailReason.PG_FAILED), any())).willReturn(true);

        paymentFacade.expire(syncPayment());

        verify(paymentCommandService).fail(thePayment(), eq(FailReason.PG_FAILED), any());
        verify(paymentCommandService, never()).fail(thePayment(), eq(FailReason.TIMEOUT), any());
    }

    @Test
    @DisplayName("만료: 만료 시각 전에 PG 결제가 끝났으면 승인한다")
    void expire_paidBeforeExpiry_approves() {
        givenPgPaidAt(EXPIRES_AT.minusSeconds(1));
        given(paymentCommandService.approve(thePayment(), any())).willReturn(PaymentApproveResult.approved(mock(PaymentConfirmResponse.class)));

        paymentFacade.expire(syncPayment());

        verify(paymentCommandService).approve(thePayment(), any());
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("만료: 만료 시각 이후 PG 결제가 끝났으면 TIMEOUT 실패 처리 후 PG를 취소한다")
    void expire_paidAfterExpiry_failsAndCancels() {
        givenPgPaidAt(EXPIRES_AT.plusSeconds(1));

        paymentFacade.expire(syncPayment());

        verify(paymentCommandService).fail(thePayment(), eq(FailReason.TIMEOUT), any());
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
        verify(paymentCommandService, never()).approve(any(), any());
    }

    // 승인 거절 (1:N - 같은 주문의 다른 결제가 먼저 승인됐거나 주문이 취소된 경우)

    @Test
    @DisplayName("confirm: 주문이 결제할 수 없는 상태라 승인이 거절되면 PG를 취소하고 PAYMENT_ORDER_NOT_PAYABLE")
    void confirm_orderNotPayable_cancelsPg() {
        newPayment();
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any()))
                .willReturn(PaymentApproveResult.rejected(PaymentApproveResult.Rejection.ORDER_NOT_PAYABLE));

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), eq("결제할 수 없는 주문의 결제 자동 취소"), isNull());
    }

    @Test
    @DisplayName("confirm: 읽은 뒤 다른 요청이 실패 처리해 승인이 거절되면 PG를 보상 취소하고 ALREADY_PROCESSED_PAYMENT")
    void confirm_failedMeanwhile_compensates() {
        newPayment();
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any()))
                .willReturn(PaymentApproveResult.rejected(PaymentApproveResult.Rejection.PAYMENT_ALREADY_FAILED));

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), eq("실패 처리된 결제의 승인 건 보상 취소"), isNull());
    }

    @Test
    @DisplayName("confirm: 승인 거절 후 PG 취소가 실패해도 거절 이유로 응답한다")
    void confirm_rejected_pgCancelFails_stillRejected() {
        newPayment();
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any()))
                .willReturn(PaymentApproveResult.rejected(PaymentApproveResult.Rejection.ORDER_NOT_PAYABLE));
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> confirm(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    }

    @Test
    @DisplayName("웹훅: 승인이 거절되면 PG를 취소한다")
    void sync_rejected_cancelsPg() {
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any()))
                .willReturn(PaymentApproveResult.rejected(PaymentApproveResult.Rejection.ORDER_NOT_PAYABLE));

        assertThat(paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isTrue();
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), eq("결제할 수 없는 주문의 결제 자동 취소"), isNull());
    }

    @Test
    @DisplayName("웹훅: 승인 거절 후 PG 취소가 실패하면 예외를 던져 재전송으로 다시 시도한다")
    void sync_rejected_pgCancelFails_throws() {
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any()))
                .willReturn(PaymentApproveResult.rejected(PaymentApproveResult.Rejection.ORDER_NOT_PAYABLE));
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> paymentFacade.syncWithPg(syncPayment(), PaymentChangedBy.WEBHOOK)).isInstanceOf(IllegalStateException.class);
    }

    // preparePayment

    private Payment newPaymentForOrder() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(MEMBER_ID).totalAmount(AMOUNT).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return Payment.builder().order(order).portonePaymentId("payment-new").amount(AMOUNT)
                .expiresAt(EXPIRES_AT).build();
    }

    @Test
    @DisplayName("결제 준비: 이전 결제 대기 건이 없으면 새 결제를 만들어 결제창 정보를 돌려준다")
    void prepare_noPrevious_createsPayment() {
        Payment created = newPaymentForOrder();
        given(paymentService.findReady(ORDER_ID)).willReturn(List.of());
        given(paymentCommandService.create(ORDER_ID)).willReturn(created);
        given(paymentService.getOrderName(ORDER_ID)).willReturn("대파 외 1건");

        PaymentPrepareResponse response = paymentFacade.preparePayment(MEMBER_ID, ORDER_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.portonePaymentId()).isEqualTo("payment-new");
        assertThat(response.orderName()).isEqualTo("대파 외 1건");
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    @DisplayName("결제 준비: 결제할 수 없는 주문이면 PG를 조회하지 않고 결제도 만들지 않는다")
    void prepare_invalidOrder_rejected() {
        willThrow(new BusinessException(ErrorCode.ORDER_ACCESS_DENIED))
                .given(paymentService).validatePayableOrder(OTHER_MEMBER_ID, ORDER_ID);

        assertThatThrownBy(() -> paymentFacade.preparePayment(OTHER_MEMBER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
        verifyNoInteractions(paymentGateway, paymentCommandService);
    }

    @Test
    @DisplayName("결제 준비: 창 닫기 요청 없이 남은 결제 대기 건은 PG 미결제를 확인하고 SUPERSEDED로 정리한 뒤 새로 만든다")
    void prepare_previousReady_supersededBeforeCreate() {
        given(paymentService.findReady(ORDER_ID)).willReturn(List.of(syncPayment()));
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());
        given(paymentCommandService.create(ORDER_ID)).willReturn(newPaymentForOrder());

        paymentFacade.preparePayment(MEMBER_ID, ORDER_ID);

        InOrder inOrder = inOrder(paymentCommandService);
        inOrder.verify(paymentCommandService).fail(thePayment(), eq(FailReason.SUPERSEDED), any());
        inOrder.verify(paymentCommandService).create(ORDER_ID);
    }

    @Test
    @DisplayName("결제 준비: 이전 결제가 PG에서 끝났으면 승인하고 SUPERSEDED로 실패 처리하지 않는다. 주문이 PAID라 생성은 거절된다")
    void prepare_previousPaidAtPg_approvesInsteadOfSuperseding() {
        given(paymentService.findReady(ORDER_ID)).willReturn(List.of(syncPayment()));
        givenPgPayment("PAID", AMOUNT);
        given(paymentCommandService.approve(thePayment(), any()))
                .willReturn(PaymentApproveResult.approved(mock(PaymentConfirmResponse.class)));
        given(paymentCommandService.create(ORDER_ID))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE));

        assertThatThrownBy(() -> paymentFacade.preparePayment(MEMBER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        verify(paymentCommandService).approve(thePayment(), any());
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("결제 준비: 이전 결제의 PG 조회가 실패하면 새 결제를 만들지 않는다")
    void prepare_pgLookupFails_noCreate() {
        given(paymentService.findReady(ORDER_ID)).willReturn(List.of(syncPayment()));
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willThrow(new IllegalStateException("PG 오류"));

        assertThatThrownBy(() -> paymentFacade.preparePayment(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentCommandService, never()).create(any());
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    // 보상 취소 이력과 재시도

    @Test
    @DisplayName("보상 취소 전에 PG 결제됨을 남기고, 취소에 성공하면 취소 ID와 함께 보상 취소를 남긴다")
    void sync_compensation_recordsHistoryAroundCancel() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("PAID", AMOUNT);
        given(paymentGateway.cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull())).willReturn("cancel-1");

        paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK);

        InOrder inOrder = inOrder(paymentHistoryRecorder, paymentGateway);
        inOrder.verify(paymentHistoryRecorder).recordUnacceptedPaid(eq(payment), eq(PaymentChangedBy.WEBHOOK), anyString());
        inOrder.verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
        inOrder.verify(paymentHistoryRecorder).recordCompensationCancelled(eq(payment), eq("cancel-1"),
                eq(PaymentChangedBy.WEBHOOK), anyString());
    }

    @Test
    @DisplayName("보상 취소가 실패하면 보상 취소 기록을 남기지 않아 재시도 대상으로 남는다")
    void sync_compensationFails_noCancelledHistory() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("PAID", AMOUNT);
        willThrow(new IllegalStateException("PG 오류")).given(paymentGateway).cancelPayment(anyString(), anyString(), isNull());

        assertThatThrownBy(() -> paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentHistoryRecorder).recordUnacceptedPaid(eq(payment), eq(PaymentChangedBy.WEBHOOK), anyString());
        verify(paymentHistoryRecorder, never()).recordCompensationCancelled(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PG CANCELLED + 우리 FAILED면 보상 취소가 끝난 것으로 기록만 남긴다")
    void sync_pgCancelled_failed_recordsCompensation() {
        Payment payment = syncPayment();
        payment.fail(FailReason.AMOUNT_MISMATCH);
        givenPgPayment("CANCELLED", AMOUNT);

        assertThat(paymentFacade.syncWithPg(payment, PaymentChangedBy.WEBHOOK)).isTrue();
        verify(paymentHistoryRecorder).recordCompensationCancelled(eq(payment), isNull(), eq(PaymentChangedBy.WEBHOOK),
                anyString());
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("재시도할 때 PG에서 아직 결제 상태면 다시 보상 취소하고 스케줄러로 기록한다")
    void retryCompensation_pgPaid_cancels() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("PAID", AMOUNT);
        given(paymentGateway.cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull())).willReturn("cancel-1");

        paymentFacade.retryCompensation(payment);

        verify(paymentHistoryRecorder).recordCompensationCancelled(eq(payment), eq("cancel-1"),
                eq(PaymentChangedBy.SCHEDULER), anyString());
    }

    @Test
    @DisplayName("재시도할 때 PG에서 이미 취소됐으면 취소하지 않고 기록만 남긴다")
    void retryCompensation_pgCancelled_recordsOnly() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("CANCELLED", AMOUNT);

        paymentFacade.retryCompensation(payment);

        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
        verify(paymentHistoryRecorder).recordCompensationCancelled(eq(payment), isNull(),
                eq(PaymentChangedBy.SCHEDULER), anyString());
    }

    @Test
    @DisplayName("재시도할 때 PG 상태가 예상과 다르면 아무것도 하지 않는다")
    void retryCompensation_unexpectedPgStatus_noChange() {
        Payment payment = syncPayment();
        payment.fail(FailReason.USER_CANCELLED);
        givenPgPayment("PARTIAL_CANCELLED", AMOUNT);

        paymentFacade.retryCompensation(payment);

        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), any());
        verifyNoInteractions(paymentHistoryRecorder);
    }
}
