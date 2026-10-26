package org.example.takecareofmyfridgebe.domain.refund.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundRequested;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundSyncState;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundCommandService;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// PG 취소 같은 외부 호출은 트랜잭션 밖에서 하고, 상태 변경은 RefundCommandService에 맡긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundFacade {

    private static final String IDEMPOTENCY_KEY_PREFIX = "refund-";
    private static final String NOT_CANCELLED_REASON = "PG 취소 내역 없음 (자동 정리)";

    private final RefundCommandService refundCommandService;
    private final RefundQueryService refundQueryService;
    private final PaymentGateway paymentGateway;

    // 환불을 요청 상태로 저장한 뒤 PG에서 환불 금액만큼 부분 취소하고, 성공하면 완료 처리한다.
    // PG가 거절하면 실패로 바꾸고, 결과를 모르면 PG를 조회해 판단한다. 조회로도 확정하지 못하면 요청 상태로 돌려주고
    // 취소 웹훅이나 환불 정리 스케줄러가 마무리한다
    public RefundResponse request(Long memberId, RefundCreateRequest request) {
        RefundRequested refund = refundCommandService.request(memberId, request);
        String cancellationId;
        try {
            cancellationId = paymentGateway.cancelPayment(refund.portonePaymentId(), refund.reason(),
                    refund.refundAmount(), idempotencyKey(refund.refundId()));
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.PG_CANCEL_REJECTED) {
                throw e;
            }
            refundCommandService.fail(refund.refundId(), e.getMessage());
            throw new BusinessException(ErrorCode.REFUND_PG_REJECTED);
        } catch (RuntimeException e) {
            log.warn("PG 취소 결과를 알 수 없음, PG 조회로 확인: refundId={}", refund.refundId(), e);
            syncQuietly(refund.portonePaymentId());
            return refundQueryService.getRefund(refund.refundId());
        }

        try {
            return refundCommandService.complete(refund.refundId(), cancellationId, PaymentChangedBy.USER);
        } catch (RuntimeException e) {
            // PG에서는 돈이 돌아갔으므로 실패로 바꾸지 않는다. 웹훅·스케줄러가 PG 조회로 완료 처리한다
            log.error("PG 취소 후 환불 완료 처리 실패, 웹훅·스케줄러가 마무리 예정: refundId={}, pgCancellationId={}",
                    refund.refundId(), cancellationId, e);
            return RefundResponse.requested(refund);
        }
    }

    // PG 누적 취소 금액을 우리 기록과 맞춘다. 취소 웹훅, 결과를 모르는 환불 신청, 환불 정리 스케줄러가 부른다.
    // 결제당 진행 중 환불은 한 건이므로, PG에서 새로 취소된 금액이 그 환불 금액과 같으면 완료, 0이면 아직 취소되지 않은 것이다.
    // failNotCancelledBefore보다 먼저 신청됐는데 아직 취소되지 않은 환불은 실패로 바꾼다(null이면 바꾸지 않는다).
    // 바꾼 것이 있으면 true. PG 조회가 실패하면 예외를 그대로 던진다
    public boolean syncWithPg(String portonePaymentId, PaymentChangedBy changedBy,
                              LocalDateTime failNotCancelledBefore) {
        Optional<PaymentGatewayResponse> found = paymentGateway.getPayment(portonePaymentId);
        if (found.isEmpty()) {
            // PG에 결제가 없으면 취소도 일어날 수 없으므로 오래된 요청 상태 환불은 실패로 정리한다
            log.warn("환불 대조할 결제가 PG에 없음: portonePaymentId={}", portonePaymentId);
            return failStale(refundQueryService.getSyncState(portonePaymentId), failNotCancelledBefore);
        }
        PaymentGatewayResponse pgPayment = found.get();
        RefundSyncState state = refundQueryService.getSyncState(portonePaymentId);
        if (!state.paid()) {
            return false;
        }

        long uncovered = pgPayment.cancelledAmount() - state.knownCancelledAmount();
        if (!state.requestedRefunds().isEmpty()) {
            RefundSyncState.RequestedRefund refund = state.requestedRefunds().get(0);
            if (uncovered == refund.amount()) {
                refundCommandService.complete(refund.refundId(),
                        findCancellationId(pgPayment, refund.amount(), state.recordedCancellationIds()), changedBy);
                return true;
            }
            if (uncovered == 0) {
                return failStale(state, failNotCancelledBefore);
            }
            log.error("PG 취소 금액이 환불 기록과 맞지 않음, 수동 확인 필요: portonePaymentId={}, PG 누적 취소={}, 기록된 취소={}, 진행 중 환불={}",
                    portonePaymentId, pgPayment.cancelledAmount(), state.knownCancelledAmount(), refund);
            return false;
        }

        if (uncovered > 0) {
            log.warn("환불 기록 없는 PG 취소 발견, 결제 이력에만 남김: portonePaymentId={}, 금액={}", portonePaymentId, uncovered);
            refundCommandService.recordUnknownCancellation(state.paymentId(), uncovered,
                    findCancellationId(pgPayment, uncovered, state.recordedCancellationIds()), changedBy);
            return true;
        }
        if (uncovered < 0) {
            log.error("기록된 취소 금액이 PG 누적 취소보다 큼, 수동 확인 필요: portonePaymentId={}, PG 누적 취소={}, 기록된 취소={}",
                    portonePaymentId, pgPayment.cancelledAmount(), state.knownCancelledAmount());
        }
        return false;
    }

    // PG에서 취소되지 않은 것이 확실할 때 부른다. failNotCancelledBefore보다 먼저 신청된 요청 상태 환불을 실패로 바꾼다
    private boolean failStale(RefundSyncState state, LocalDateTime failNotCancelledBefore) {
        if (failNotCancelledBefore == null) {
            return false;
        }
        boolean changed = false;
        for (RefundSyncState.RequestedRefund refund : state.requestedRefunds()) {
            if (refund.requestedAt().isBefore(failNotCancelledBefore)) {
                log.warn("PG에서 취소되지 않은 오래된 환불 실패 처리: refundId={}", refund.refundId());
                refundCommandService.fail(refund.refundId(), NOT_CANCELLED_REASON);
                changed = true;
            }
        }
        return changed;
    }

    // 같은 환불은 몇 번 요청해도 PG에서 한 번만 취소되도록 환불 ID로 늘 같은 키를 만든다.
    // PortOne은 UUID 형식의 키만 받으므로 이름 기반 UUID로 만든다
    static String idempotencyKey(Long refundId) {
        return UUID.nameUUIDFromBytes((IDEMPOTENCY_KEY_PREFIX + refundId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // 응답을 기다리는 사용자에게는 조회 실패를 알리지 않는다. 요청 상태로 돌려주고 웹훅·스케줄러가 마무리한다
    private void syncQuietly(String portonePaymentId) {
        try {
            syncWithPg(portonePaymentId, PaymentChangedBy.USER, null);
        } catch (RuntimeException e) {
            log.warn("환불 결과 확인용 PG 조회 실패, 웹훅·스케줄러가 마무리 예정: portonePaymentId={}", portonePaymentId, e);
        }
    }

    // 아직 기록하지 않은 완료된 PG 취소 중 금액이 같은 가장 최근 건. 찾지 못하면 null
    private String findCancellationId(PaymentGatewayResponse pgPayment, long amount, Set<String> recordedIds) {
        return pgPayment.cancellations().stream()
                .filter(cancellation -> cancellation.succeeded() && cancellation.totalAmount() == amount
                        && !recordedIds.contains(cancellation.id()))
                .reduce((first, second) -> second)
                .map(PaymentGatewayResponse.Cancellation::id)
                .orElse(null);
    }
}
