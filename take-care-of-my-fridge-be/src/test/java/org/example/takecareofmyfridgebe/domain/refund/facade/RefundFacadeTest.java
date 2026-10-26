package org.example.takecareofmyfridgebe.domain.refund.facade;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundRequested;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundSyncState;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundCommandService;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RefundFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long PAYMENT_ID = 50L;
    private static final Long REFUND_ID = 100L;
    private static final String PORTONE_PAYMENT_ID = "payment-1";
    private static final String IDEMPOTENCY_KEY = RefundFacade.idempotencyKey(REFUND_ID);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    @Mock
    private RefundCommandService refundCommandService;

    @Mock
    private RefundQueryService refundQueryService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private RefundFacade refundFacade;

    private final RefundCreateRequest request = new RefundCreateRequest(
            ORDER_ID, List.of(new RefundCreateRequest.Item(1L, 2)), RefundReasonType.CHANGE_OF_MIND, null);

    private final RefundResponse completed = new RefundResponse(REFUND_ID, RefundStatus.COMPLETED, 6000L, NOW);
    private final RefundResponse requested = new RefundResponse(REFUND_ID, RefundStatus.REQUESTED, 6000L, null);

    private void givenRequested() {
        given(refundCommandService.request(MEMBER_ID, request))
                .willReturn(new RefundRequested(REFUND_ID, PORTONE_PAYMENT_ID, 6000L, "단순 변심"));
    }

    // 결제 금액 10,000원 중 이미 완료된 환불 2,000원이 있는 결제
    private void givenPg(long cancelledAmount, PaymentGatewayResponse.Cancellation... cancellations) {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.of(new PaymentGatewayResponse(
                PORTONE_PAYMENT_ID, "PARTIAL_CANCELLED", 10000L, cancelledAmount, NOW.minusDays(1),
                List.of(cancellations))));
    }

    private void givenState(boolean paid, List<RefundSyncState.RequestedRefund> requestedRefunds) {
        given(refundQueryService.getSyncState(PORTONE_PAYMENT_ID)).willReturn(
                new RefundSyncState(PAYMENT_ID, paid, 2000L, requestedRefunds, Set.of("cancellation-old")));
    }

    private RefundSyncState.RequestedRefund requestedRefund(LocalDateTime requestedAt) {
        return new RefundSyncState.RequestedRefund(REFUND_ID, 6000L, requestedAt);
    }

    // request

    @Test
    @DisplayName("요청 상태로 저장한 뒤 환불 ID 멱등 키로 PG를 부분 취소하고 취소 ID로 완료 처리한 결과를 돌려준다")
    void request_cancelsPgThenCompletes() {
        givenRequested();
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY))
                .willReturn("cancellation-1");
        given(refundCommandService.complete(REFUND_ID, "cancellation-1", PaymentChangedBy.USER)).willReturn(completed);

        RefundResponse response = refundFacade.request(MEMBER_ID, request);

        assertThat(response).isEqualTo(completed);
        InOrder inOrder = inOrder(refundCommandService, paymentGateway);
        inOrder.verify(refundCommandService).request(MEMBER_ID, request);
        inOrder.verify(paymentGateway).cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY);
        inOrder.verify(refundCommandService).complete(REFUND_ID, "cancellation-1", PaymentChangedBy.USER);
    }

    @Test
    @DisplayName("PG가 취소를 거절하면 환불을 실패로 바꾸고 REFUND_012로 거절한다")
    void request_pgRejected_failsRefund() {
        givenRequested();
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY))
                .willThrow(new BusinessException(ErrorCode.PG_CANCEL_REJECTED, "PG 취소 거절: INVALID_REQUEST"));

        assertThatThrownBy(() -> refundFacade.request(MEMBER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REFUND_PG_REJECTED);
        verify(refundCommandService).fail(REFUND_ID, "PG 취소 거절: INVALID_REQUEST");
        verify(refundCommandService, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("PG 취소 결과를 모르면 PG를 조회하고, 이번 금액만큼 취소됐으면 완료 처리한다")
    void request_unknown_pgCancelled_completes() {
        givenRequested();
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY))
                .willThrow(new ResourceAccessException("timeout"));
        givenPg(8000L, new PaymentGatewayResponse.Cancellation("cancellation-1", 6000L, true));
        givenState(true, List.of(requestedRefund(NOW)));
        given(refundQueryService.getRefund(REFUND_ID)).willReturn(completed);

        RefundResponse response = refundFacade.request(MEMBER_ID, request);

        assertThat(response).isEqualTo(completed);
        verify(refundCommandService).complete(REFUND_ID, "cancellation-1", PaymentChangedBy.USER);
    }

    @Test
    @DisplayName("PG 취소 결과를 모르고 PG에서도 아직 취소되지 않았으면 실패로 바꾸지 않고 요청 상태로 돌려준다")
    void request_unknown_notCancelled_staysRequested() {
        givenRequested();
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY))
                .willThrow(new ResourceAccessException("timeout"));
        givenPg(2000L);
        givenState(true, List.of(requestedRefund(NOW)));
        given(refundQueryService.getRefund(REFUND_ID)).willReturn(requested);

        RefundResponse response = refundFacade.request(MEMBER_ID, request);

        assertThat(response.status()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundCommandService, never()).complete(any(), any(), any());
        verify(refundCommandService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("PG 취소 결과를 모르고 PG 조회도 실패하면 요청 상태로 돌려준다")
    void request_unknown_lookupFails_staysRequested() {
        givenRequested();
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY))
                .willThrow(new ResourceAccessException("timeout"));
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willThrow(new ResourceAccessException("timeout"));
        given(refundQueryService.getRefund(REFUND_ID)).willReturn(requested);

        RefundResponse response = refundFacade.request(MEMBER_ID, request);

        assertThat(response.status()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundCommandService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("PG 취소 후 완료 처리가 실패하면 실패로 바꾸지 않고 요청 상태로 돌려준다")
    void request_completeFails_returnsRequested() {
        givenRequested();
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L, IDEMPOTENCY_KEY))
                .willReturn("cancellation-1");
        given(refundCommandService.complete(REFUND_ID, "cancellation-1", PaymentChangedBy.USER))
                .willThrow(new IllegalStateException("DB 오류"));

        RefundResponse response = refundFacade.request(MEMBER_ID, request);

        assertThat(response).isEqualTo(requested);
        verify(refundCommandService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("멱등 키는 PortOne이 받는 UUID 형식이고, 같은 환불이면 늘 같고 다른 환불이면 다르다")
    void idempotencyKey_isStableUuid() {
        assertThat(RefundFacade.idempotencyKey(REFUND_ID))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .isEqualTo(RefundFacade.idempotencyKey(REFUND_ID))
                .isNotEqualTo(RefundFacade.idempotencyKey(REFUND_ID + 1));
    }

    // syncWithPg

    @Test
    @DisplayName("PG에서 새로 취소된 금액이 진행 중 환불 금액과 같으면 아직 기록하지 않은 취소 ID로 완료 처리한다")
    void sync_matchingAmount_completes() {
        givenPg(8000L,
                new PaymentGatewayResponse.Cancellation("cancellation-old", 2000L, true),
                new PaymentGatewayResponse.Cancellation("cancellation-failed", 6000L, false),
                new PaymentGatewayResponse.Cancellation("cancellation-1", 6000L, true));
        givenState(true, List.of(requestedRefund(NOW)));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).isTrue();
        verify(refundCommandService).complete(REFUND_ID, "cancellation-1", PaymentChangedBy.WEBHOOK);
    }

    @Test
    @DisplayName("PG에서 아직 취소되지 않았으면 기준 시각이 없을 때는 아무것도 바꾸지 않는다")
    void sync_notCancelled_withoutDeadline_noChange() {
        givenPg(2000L);
        givenState(true, List.of(requestedRefund(NOW.minusHours(1))));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).isFalse();
        verify(refundCommandService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("기준 시각보다 먼저 신청됐는데 PG에서 취소되지 않은 환불은 실패로 바꾼다")
    void sync_staleNotCancelled_fails() {
        givenPg(2000L);
        givenState(true, List.of(requestedRefund(NOW.minusMinutes(30))));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.SCHEDULER, NOW.minusMinutes(10)))
                .isTrue();
        verify(refundCommandService).fail(REFUND_ID, "PG 취소 내역 없음 (자동 정리)");
    }

    @Test
    @DisplayName("기준 시각 이후에 신청된 환불은 아직 기다린다")
    void sync_recentNotCancelled_waits() {
        givenPg(2000L);
        givenState(true, List.of(requestedRefund(NOW.minusMinutes(5))));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.SCHEDULER, NOW.minusMinutes(10)))
                .isFalse();
        verify(refundCommandService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("진행 중 환불이 없는데 PG 취소 금액이 늘었으면 환불 기록 없는 취소로 결제 이력에만 남긴다")
    void sync_unknownCancellation_recorded() {
        givenPg(5000L, new PaymentGatewayResponse.Cancellation("console-1", 3000L, true));
        givenState(true, List.of());

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).isTrue();
        verify(refundCommandService).recordUnknownCancellation(PAYMENT_ID, 3000L, "console-1", PaymentChangedBy.WEBHOOK);
    }

    @Test
    @DisplayName("PG 취소 금액이 진행 중 환불 금액과 맞지 않으면 바꾸지 않는다")
    void sync_mismatch_noChange() {
        givenPg(5000L);
        givenState(true, List.of(requestedRefund(NOW)));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).isFalse();
        verify(refundCommandService, never()).complete(any(), any(), any());
        verify(refundCommandService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("결제 완료가 아닌 결제는 환불 쪽에서 맞추지 않는다")
    void sync_notPaid_noChange() {
        givenPg(10000L);
        givenState(false, List.of());

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).isFalse();
        verifyNoInteractions(refundCommandService);
    }

    @Test
    @DisplayName("PG에 결제가 없어도 기준 시각이 없으면 아무것도 바꾸지 않는다")
    void sync_pgMissing_withoutDeadline_noChange() {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());
        givenState(true, List.of(requestedRefund(NOW.minusHours(1))));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.WEBHOOK, null)).isFalse();
        verifyNoInteractions(refundCommandService);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("PG에 결제가 없으면 취소될 수 없으므로 오래된 요청 상태 환불을 실패로 바꾼다")
    void sync_pgMissing_staleFails() {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());
        givenState(true, List.of(requestedRefund(NOW.minusMinutes(30))));

        assertThat(refundFacade.syncWithPg(PORTONE_PAYMENT_ID, PaymentChangedBy.SCHEDULER, NOW.minusMinutes(10)))
                .isTrue();
        verify(refundCommandService).fail(REFUND_ID, "PG 취소 내역 없음 (자동 정리)");
    }
}
