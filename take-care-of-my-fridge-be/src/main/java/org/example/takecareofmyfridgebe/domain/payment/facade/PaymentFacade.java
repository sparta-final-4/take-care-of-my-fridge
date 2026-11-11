package org.example.takecareofmyfridgebe.domain.payment.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPaymentRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult.Rejection;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPrepareRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyPayResult;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentCheckout;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyCrypto;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentCommandService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

// PG 조회·취소 같은 외부 호출은 트랜잭션 밖에서 하고, 상태 변경은 PaymentCommandService에 맡긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private static final String COMPENSATION_CANCEL_REASON = "실패 처리된 결제의 승인 건 보상 취소";
    private static final String ORDER_NOT_PAYABLE_CANCEL_REASON = "결제할 수 없는 주문의 결제 자동 취소";
    private static final String UNACCEPTED_PAID_REASON = "받지 않은 결제의 PG 결제 완료 확인, 보상 취소 대상";
    private static final String PG_CANCELLED_CONFIRMED_REASON = "PG에서 취소된 것을 확인";

    private final PaymentService paymentService;
    private final PaymentCommandService paymentCommandService;
    private final PaymentGateway paymentGateway;
    private final PaymentHistoryRecorder paymentHistoryRecorder;
    private final BillingKeyService billingKeyService;
    private final BillingKeyCrypto billingKeyCrypto;
    private final BillingKeyGateway billingKeyGateway;
    private final PaymentPinService paymentPinService;

    // 결제하기를 누를 때 프론트가 호출한다. 응답의 portonePaymentId와 고른 결제대행사의 채널 값으로 결제창을 띄운다.
    // 창 닫기 요청이 오지 않아 결제 대기로 남은 이전 결제가 있으면 정리한 뒤 새 결제를 만든다.
    // 규칙: 이전 결제가 PG에서 이미 끝났으면 그 결제를 승인하고 새 결제는 만들지 않는다.
    //  아직 진행 중이면 가장 최근에 연 결제창만 인정한다. 이전 창(다른 탭·기기)에서 나중에 결제되면 FAILED 결제라 환불된다.
    //  서버는 "다른 탭에서 결제 중"과 "브라우저 종료로 버려진 창"을 구분할 수 없어, 버려진 창 때문에 재결제가 막히지 않도록 이렇게 정했다
    public PaymentPrepareResponse preparePayment(Long memberId, Long orderId, PaymentPrepareRequest request) {
        paymentService.validatePayableOrder(memberId, orderId);
        closePreviousReady(orderId);

        // 결제창 값을 만들지 못하면 결제창을 열 수 없는 결제가 남지 않도록 결제를 만들기 전에 구한다
        PaymentCheckout checkout = paymentGateway.getCheckout(request.pgProvider());
        Payment payment = paymentCommandService.create(orderId, request.pgProvider());
        return PaymentPrepareResponse.of(payment, paymentService.getOrderName(orderId), checkout);
    }

    // 결제창에서 결제를 마친 뒤 프론트가 호출한다
    public PaymentConfirmResponse confirmPayment(Long memberId, PaymentConfirmRequest request) {
        Payment payment = getOwnedPayment(memberId, request.portonePaymentId());

        // 다른 주문의 결제 ID로 바꿔치기한 요청을 막는다
        if (!request.orderId().equals(payment.getOrder().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentConfirmResponse.from(payment);
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new BusinessException(errorCodeOf(payment.getFailReason()));
        }
        return confirmWithPg(payment);
    }

    // 등록 카드 결제. 결제창 없이 서버가 PortOne에 청구하고, 결제됐으면 결제창 결제 확정과 같은 규칙으로 승인한다
    public PaymentConfirmResponse payWithBillingKey(Long memberId, Long orderId, BillingKeyPaymentRequest request) {
        paymentService.validatePayableOrder(memberId, orderId);
        BillingKey card = billingKeyService.getUsableCard(memberId, request.cardId());
        // PortOne에 청구하기 전에 확인한다. 틀리면 결제를 만들지 않고, 틀린 횟수는 따로 커밋된다
        paymentPinService.verify(memberId, request.pin());
        // 이전 결제 대기 건을 닫기 전에 푼다. 풀지 못하면(키 설정 오류 등) 아무것도 바꾸지 않고 실패한다
        String billingKey = decryptForPayment(card);
        closePreviousReady(orderId);

        // 빌링키는 토스페이먼츠 정기결제 채널로 발급했다. 승인할 때 PG가 알려 준 실제 결제대행사로 고친다
        Payment payment = paymentCommandService.create(orderId, PgProvider.TOSSPAYMENTS, card);
        BillingKeyPayResult result = billingKeyGateway.pay(payment.getPortonePaymentId(), billingKey,
                paymentService.getOrderName(orderId), payment.getAmount(), BillingKeyService.customerIdOf(memberId));

        if (result == BillingKeyPayResult.PAID) {
            return confirmWithPg(payment);
        }
        return settleByPg(payment, result);
    }

    // 풀지 못하는 것은 회원 입력이 아니라 서버 키 설정·저장 값 문제다. 원인(키 ID 등)은 로그에만 남기고 서버 오류로 답한다
    private String decryptForPayment(BillingKey card) {
        try {
            return billingKeyCrypto.decrypt(card);
        } catch (IllegalStateException e) {
            log.error("등록 카드 빌링키를 풀지 못함: cardId={}", card.getId(), e);
            throw new BusinessException(ErrorCode.BILLING_KEY_UNAVAILABLE);
        }
    }

    // 결제되지 않았거나(거절) 결과를 모를 때 PG를 한 번 조회해 실제 상태로 정한다.
    // 결제됐으면 승인한다. 거절이면서 PG에도 결제가 없거나, PG가 실패로 확정했으면 실패 처리하고 거절로 답한다.
    // 그 밖(결과 모름이면서 PG에 아직 결제 없음, 조회 실패)은 결제 대기로 두고 웹훅·만료 스케줄러가 마무리한다
    private PaymentConfirmResponse settleByPg(Payment payment, BillingKeyPayResult result) {
        Optional<PaymentGatewayResponse> found;
        try {
            found = paymentGateway.getPayment(payment.getPortonePaymentId());
        } catch (RuntimeException e) {
            log.warn("등록 카드 결제 결과 조회 실패, 웹훅·만료 스케줄러가 마무리: portonePaymentId={}",
                    payment.getPortonePaymentId(), e);
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_PENDING);
        }
        if (found.isPresent() && found.get().isPaid()) {
            return confirmWithPg(payment, found.get());
        }
        boolean pgFailed = found.isPresent() && found.get().isFailed();
        if (pgFailed || (result == BillingKeyPayResult.REJECTED && found.isEmpty())) {
            // 주문은 결제 대기로 남아 다른 카드나 결제 수단으로 다시 결제할 수 있다
            paymentCommandService.fail(payment, FailReason.PG_FAILED, PaymentChangedBy.USER);
            throw new BusinessException(ErrorCode.BILLING_PAYMENT_REJECTED);
        }
        throw new BusinessException(ErrorCode.PAYMENT_RESULT_PENDING);
    }

    // 이전 결제가 PG에서 끝났으면 웹훅과 같은 규칙으로 처리한다. 승인됐으면 주문이 PAID라 뒤의 결제 생성이 거절된다.
    // PG 조회가 실패하면 예외를 그대로 던져 새 결제를 만들지 않는다
    // 등록 카드 결제는 버려진 결제창이 없고 서버가 PG에 청구 중일 수 있다. 밀어내면 같은 주문이 두 번 청구되므로
    // PG에서 이미 끝났으면 그 결과로 처리하고, 아니면 진행 중으로 거절한다. 남은 건은 웹훅·만료 스케줄러가 마무리한다
    private void closePreviousReady(Long orderId) {
        for (Payment previous : paymentService.findReady(orderId)) {
            if (previous.getBillingKey() != null) {
                if (!syncWithPg(previous, PaymentChangedBy.USER)) {
                    throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
                }
                continue;
            }
            if (closeReady(previous, FailReason.SUPERSEDED, PaymentChangedBy.USER)) {
                log.info("이전 결제 대기 정리: portonePaymentId={}", previous.getPortonePaymentId());
            }
        }
    }

    // PG를 다시 조회해 결제 완료·금액·만료를 확인하고 승인한다. 받지 않을 결제는 실패 처리 후 PG를 취소한다
    private PaymentConfirmResponse confirmWithPg(Payment payment) {
        PaymentGatewayResponse pgPayment = paymentGateway.getPayment(payment.getPortonePaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_PAID));
        return confirmWithPg(payment, pgPayment);
    }

    private PaymentConfirmResponse confirmWithPg(Payment payment, PaymentGatewayResponse pgPayment) {

        if (!pgPayment.isPaid()) {
            // PG가 실패로 확정한 결제는 바로 실패 처리로 상태 변경
            if (pgPayment.isFailed()) {
                paymentCommandService.fail(payment, FailReason.PG_FAILED, PaymentChangedBy.USER);
            }
            // 아직 진행 중인 결제는 상태를 바꾸지 않는다. 만료 시각이 지나면 PaymentTimeoutScheduler가 정리한다
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PAID);
        }

        PaidDecision decision = decidePaid(payment, pgPayment);
        if (decision == PaidDecision.APPROVE) {
            PaymentApproveResult result = paymentCommandService.approve(payment, pgPayment.pgProvider(),
                    PaymentChangedBy.USER);
            if (result.isApproved()) {
                return result.response();
            }
            // 승인하지 못한 결제는 실패로 기록됐으므로 PG 결제를 되돌린다
            compensateQuietly(payment, cancelReasonOf(result.rejection()));
            throw new BusinessException(errorCodeOf(result.rejection()));
        }
        // 락을 잡고 실패 처리가 반영된 경우에만 PG를 취소해 DB PAID + PG 취소 상태를 막는다
        if (paymentCommandService.fail(payment, decision.failReason, PaymentChangedBy.USER)) {
            compensateQuietly(payment, decision.cancelReason);
        } else {
            log.error("받지 않을 결제가 이미 처리됨, PG 취소 생략: portonePaymentId={}", payment.getPortonePaymentId());
        }
        throw new BusinessException(decision.errorCode);
    }

    // 사용자가 결제창을 닫았을 때 프론트가 호출한다. 이 결제 시도만 실패 처리하고 주문은 다시 결제할 수 있게 둔다
    public void failByUser(Long memberId, String portonePaymentId) {
        Payment payment = getOwnedPayment(memberId, portonePaymentId);

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        // 결제가 실제로 끝났는데 취소 요청이 온 경우 실패 처리하지 않는다
        Optional<PaymentGatewayResponse> pgPayment = paymentGateway.getPayment(portonePaymentId);
        if (pgPayment.isPresent() && pgPayment.get().isPaid()) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        // PG 조회와 실패 처리 사이에 결제가 완료되면 Transaction.Paid 웹훅에서 보상 취소한다
        paymentCommandService.fail(payment, FailReason.USER_CANCELLED, PaymentChangedBy.USER);
    }

    // PG 상태에 맞춰 결제 상태를 바꾸고, 바꾼 것이 있으면 true. changedBy는 결제 이력에 남길 호출 주체
    public boolean syncWithPg(Payment payment, PaymentChangedBy changedBy) {
        Optional<PaymentGatewayResponse> found = paymentGateway.getPayment(payment.getPortonePaymentId());
        if (found.isEmpty()) {
            return false;
        }

        PaymentGatewayResponse pgPayment = found.get();
        if (pgPayment.isPaid()) {
            return switch (payment.getStatus()) {
                case READY -> {
                    PaidDecision decision = decidePaid(payment, pgPayment);
                    if (decision == PaidDecision.APPROVE) {
                        PaymentApproveResult result = paymentCommandService.approve(payment, pgPayment.pgProvider(),
                                changedBy);
                        // 읽은 뒤 다른 요청이 실패 처리했거나, 같은 주문의 다른 결제가 먼저 승인됐거나, 주문이 취소된 경우다.
                        // 취소가 실패하면 예외를 던져 웹훅 재전송으로 다시 시도한다
                        if (!result.isApproved()) {
                            compensate(payment, cancelReasonOf(result.rejection()), changedBy);
                        }
                    } else {
                        // 다른 요청이 먼저 실패 처리했어도 PG 결제는 남아 있으므로 취소한다.
                        // 금액 불일치·만료 결제는 어느 요청도 승인할 수 없어 DB PAID + PG 취소 상태는 생기지 않는다.
                        // 취소가 실패하면 예외를 던져 웹훅 재전송으로 다시 시도한다
                        paymentCommandService.fail(payment, decision.failReason, changedBy);
                        compensate(payment, decision.cancelReason, changedBy);
                    }
                    yield true;
                }
                case FAILED -> {
                    log.warn("실패 처리된 결제가 PG에서 승인됨, 보상 취소: portonePaymentId={}", payment.getPortonePaymentId());
                    compensate(payment, COMPENSATION_CANCEL_REASON, changedBy);
                    yield true;
                }
                case PAID -> false;
            };
        }

        // 결제 완료된 결제의 PG 취소·부분 취소는 환불 쪽(RefundFacade.syncWithPg)이 맞춘다
        if (pgPayment.isFailed() && payment.getStatus() == PaymentStatus.READY) {
            return paymentCommandService.fail(payment, FailReason.PG_FAILED, changedBy);
        }
        // 보상 취소를 다른 경로(재시도, 콘솔)가 마쳤으면 기록만 남겨 재시도 대상에서 뺀다
        if (pgPayment.isCancelled() && payment.getStatus() == PaymentStatus.FAILED) {
            paymentHistoryRecorder.recordCompensationCancelled(payment, null, changedBy, PG_CANCELLED_CONFIRMED_REASON);
            return true;
        }
        return false;
    }

    // 스케줄러가 만료 시각이 지난 결제 대기 건에 호출한다
    public void expire(Payment payment) {
        if (closeReady(payment, FailReason.TIMEOUT, PaymentChangedBy.SCHEDULER)) {
            log.info("결제 대기 만료 처리: portonePaymentId={}", payment.getPortonePaymentId());
        }
    }

    // 스케줄러가 보상 취소가 끝나지 않은 실패 결제에 호출한다. PG에서 아직 결제 상태면 다시 취소하고,
    // 이미 취소됐으면 기록만 남긴다. 취소가 실패하면 예외를 던져 다음 실행에서 다시 시도한다
    public void retryCompensation(Payment payment) {
        Optional<PaymentGatewayResponse> found = paymentGateway.getPayment(payment.getPortonePaymentId());
        if (found.isEmpty()) {
            log.error("보상 취소 대상 결제가 PG에 없음, 수동 확인 필요: portonePaymentId={}", payment.getPortonePaymentId());
            return;
        }
        PaymentGatewayResponse pgPayment = found.get();
        if (pgPayment.isPaid()) {
            compensate(payment, COMPENSATION_CANCEL_REASON, PaymentChangedBy.SCHEDULER);
            log.info("보상 취소 재시도 성공: portonePaymentId={}", payment.getPortonePaymentId());
            return;
        }
        if (pgPayment.isCancelled()) {
            paymentHistoryRecorder.recordCompensationCancelled(payment, null, PaymentChangedBy.SCHEDULER,
                    PG_CANCELLED_CONFIRMED_REASON);
            return;
        }
        log.error("보상 취소 대상 결제의 PG 상태가 예상과 다름, 수동 확인 필요: portonePaymentId={}, PG 상태={}",
                payment.getPortonePaymentId(), pgPayment.status());
    }

    // 결제 대기 건을 정리한다. PG에서 결제가 끝났으면 웹훅과 같은 규칙으로 처리하고, 아니면 reason으로 실패 처리한다.
    // reason으로 실패 처리했으면 true
    private boolean closeReady(Payment payment, FailReason reason, PaymentChangedBy changedBy) {
        if (syncWithPg(payment, changedBy)) {
            return false;
        }
        return paymentCommandService.fail(payment, reason, changedBy);
    }

    // PG에서 결제가 완료된 READY 결제를 받을지 정한다. 금액이나 결제 시각을 확인할 수 없으면 판단하지 않는다
    private PaidDecision decidePaid(Payment payment, PaymentGatewayResponse pgPayment) {
        if (pgPayment.totalAmount() == null || pgPayment.paidAt() == null) {
            log.error("PG 결제 정보 누락: portonePaymentId={}, 금액={}, 결제 시각={}",
                    payment.getPortonePaymentId(), pgPayment.totalAmount(), pgPayment.paidAt());
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PAID);
        }
        if (!payment.getAmount().equals(pgPayment.totalAmount())) {
            log.error("결제 금액 불일치: portonePaymentId={}, 주문 금액={}, PG 금액={}",
                    payment.getPortonePaymentId(), payment.getAmount(), pgPayment.totalAmount());
            return PaidDecision.AMOUNT_MISMATCH;
        }
        if (payment.isPaidAfterExpiry(pgPayment.paidAt())) {
            log.warn("만료 시각 이후 결제 완료: portonePaymentId={}, 만료 시각={}, 결제 시각={}",
                    payment.getPortonePaymentId(), payment.getExpiresAt(), pgPayment.paidAt());
            return PaidDecision.EXPIRED;
        }
        return PaidDecision.APPROVE;
    }

    private String cancelReasonOf(Rejection rejection) {
        return switch (rejection) {
            case PAYMENT_ALREADY_FAILED -> COMPENSATION_CANCEL_REASON;
            case ORDER_NOT_PAYABLE -> ORDER_NOT_PAYABLE_CANCEL_REASON;
        };
    }

    // 웹훅·스케줄러가 confirm보다 먼저 실패 처리한 결제도 confirm이 직접 거절할 때와 같은 이유로 알린다
    private ErrorCode errorCodeOf(FailReason failReason) {
        if (failReason == null) {
            return ErrorCode.ALREADY_PROCESSED_PAYMENT;
        }
        return switch (failReason) {
            case TIMEOUT -> ErrorCode.PAYMENT_EXPIRED;
            case AMOUNT_MISMATCH -> ErrorCode.PAYMENT_AMOUNT_MISMATCH;
            case ORDER_NOT_PAYABLE -> ErrorCode.PAYMENT_ORDER_NOT_PAYABLE;
            case PG_FAILED, USER_CANCELLED, SUPERSEDED -> ErrorCode.ALREADY_PROCESSED_PAYMENT;
        };
    }

    private ErrorCode errorCodeOf(Rejection rejection) {
        return switch (rejection) {
            case PAYMENT_ALREADY_FAILED -> ErrorCode.ALREADY_PROCESSED_PAYMENT;
            case ORDER_NOT_PAYABLE -> ErrorCode.PAYMENT_ORDER_NOT_PAYABLE;
        };
    }

    // 받지 않은 결제를 PG에서 전액 취소한다. 취소 전에 "PG에서 결제됨"을 남겨 두므로, 취소가 실패하면
    // 웹훅 재전송과 보상 취소 재시도 스케줄러가 다시 시도한다. 실패하면 예외를 그대로 던진다
    private void compensate(Payment payment, String reason, PaymentChangedBy changedBy) {
        paymentHistoryRecorder.recordUnacceptedPaid(payment, changedBy, UNACCEPTED_PAID_REASON);
        String cancellationId = paymentGateway.cancelPayment(payment.getPortonePaymentId(), reason, null);
        paymentHistoryRecorder.recordCompensationCancelled(payment, cancellationId, changedBy, reason);
    }

    private Payment getOwnedPayment(Long memberId, String portonePaymentId) {
        Payment payment = paymentService.getWithOrder(portonePaymentId);
        if (!payment.getOrder().getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return payment;
    }

    // confirm 응답은 받지 않은 이유(금액 불일치·만료·결제할 수 없는 주문)로 끝나야 하므로 PG 취소 실패를 삼킨다.
    // 결제 실패는 이미 기록됐고, Transaction.Paid 웹훅과 보상 취소 재시도 스케줄러가 다시 취소한다
    private void compensateQuietly(Payment payment, String reason) {
        try {
            compensate(payment, reason, PaymentChangedBy.USER);
        } catch (Exception e) {
            log.error("PG 자동 취소 실패, 웹훅·스케줄러가 재시도 예정: portonePaymentId={}", payment.getPortonePaymentId(), e);
        }
    }

    // PG에서 결제가 완료된 READY 결제의 처리 방향
    private enum PaidDecision {
        APPROVE(null, null, null),
        AMOUNT_MISMATCH(FailReason.AMOUNT_MISMATCH, "결제 금액 불일치 자동 취소", ErrorCode.PAYMENT_AMOUNT_MISMATCH),
        EXPIRED(FailReason.TIMEOUT, "결제 가능 시간 초과 자동 취소", ErrorCode.PAYMENT_EXPIRED);

        private final FailReason failReason;
        private final String cancelReason;
        private final ErrorCode errorCode;

        PaidDecision(FailReason failReason, String cancelReason, ErrorCode errorCode) {
            this.failReason = failReason;
            this.cancelReason = cancelReason;
            this.errorCode = errorCode;
        }
    }
}
