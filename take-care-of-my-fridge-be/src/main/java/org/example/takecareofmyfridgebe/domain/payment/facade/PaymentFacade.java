package org.example.takecareofmyfridgebe.domain.payment.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentCommandService;
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

    private static final String AMOUNT_MISMATCH_CANCEL_REASON = "결제 금액 불일치 자동 취소";
    private static final String COMPENSATION_CANCEL_REASON = "실패 처리된 결제의 승인 건 보상 취소";

    private final PaymentService paymentService;
    private final PaymentCommandService paymentCommandService;
    private final PaymentGateway paymentGateway;

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
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        PaymentGatewayResponse pgPayment = paymentGateway.getPayment(payment.getPortonePaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_PAID));

        if (!pgPayment.isPaid()) {
            // PG가 실패로 확정한 결제는 바로 실패 처리로 상태 변경
            if (pgPayment.isFailed()) {
                paymentCommandService.fail(payment.getPortonePaymentId(), FailReason.PG_FAILED);
            }
            //TODO: 아직 진행 중인 결제는 상태를 바꾸지 않고, 타임아웃 스케쥴러 로직 구현 후 처리
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PAID);
        }

        if (!amountMatches(payment, pgPayment)) {
            // 락을 잡고 실패 처리가 반영된 경우에만 PG를 취소해 DB PAID + PG 취소 상태를 막는다
            if (paymentCommandService.fail(payment.getPortonePaymentId(), FailReason.AMOUNT_MISMATCH)) {
                cancelPgPaymentQuietly(payment.getPortonePaymentId());
            } else {
                log.error("금액 불일치 결제가 이미 처리됨, PG 취소 생략: portonePaymentId={}", payment.getPortonePaymentId());
            }
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        return paymentCommandService.approve(payment.getPortonePaymentId());
    }

    // 사용자가 결제창을 닫았을 때 프론트가 호출한다
    public void failByUser(Long memberId, String portonePaymentId) {
        Payment payment = getOwnedPayment(memberId, portonePaymentId);

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        // 결제가 실제로 끝났는데 취소 요청이 온 경우 주문을 취소하지 않는다
        Optional<PaymentGatewayResponse> pgPayment = paymentGateway.getPayment(portonePaymentId);
        if (pgPayment.isPresent() && pgPayment.get().isPaid()) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        // PG 조회와 실패 처리 사이에 결제가 완료되면 Transaction.Paid 웹훅에서 보상 취소한다
        paymentCommandService.fail(portonePaymentId, FailReason.USER_CANCELLED);
    }

    // 웹훅 수신 시 PG 상태에 맞춰 결제 상태를 바꾸고, 바꾼 것이 있으면 true
    public boolean syncWithPg(Payment payment) {
        Optional<PaymentGatewayResponse> found = paymentGateway.getPayment(payment.getPortonePaymentId());
        if (found.isEmpty()) {
            return false;
        }

        PaymentGatewayResponse pgPayment = found.get();
        if (pgPayment.isPaid()) {
            return switch (payment.getStatus()) {
                case READY -> {
                    if (amountMatches(payment, pgPayment)) {
                        paymentCommandService.approve(payment.getPortonePaymentId());
                    } else {
                        // 다른 요청이 먼저 실패 처리했어도 PG 결제는 남아 있으므로 취소한다.
                        // 취소가 실패하면 예외를 던져 웹훅 재전송으로 다시 시도한다
                        paymentCommandService.fail(payment.getPortonePaymentId(), FailReason.AMOUNT_MISMATCH);
                        cancelPgPayment(payment.getPortonePaymentId(), AMOUNT_MISMATCH_CANCEL_REASON);
                    }
                    yield true;
                }
                case FAILED -> {
                    log.warn("실패 처리된 결제가 PG에서 승인됨, 보상 취소: portonePaymentId={}", payment.getPortonePaymentId());
                    cancelPgPayment(payment.getPortonePaymentId(), COMPENSATION_CANCEL_REASON);
                    yield true;
                }
                case PAID -> false;
            };
        }

        // TODO: 환불 도메인 구현 후 PAID 결제의 PG 취소·부분 취소를 환불 쪽과 동기화
        if (pgPayment.isFailed() && payment.getStatus() == PaymentStatus.READY) {
            return paymentCommandService.fail(payment.getPortonePaymentId(), FailReason.PG_FAILED);
        }
        return false;
    }

    // PG 금액이 주문 금액과 같은지 확인한다. 금액을 확인할 수 없으면 승인도 취소도 하지 않는다
    private boolean amountMatches(Payment payment, PaymentGatewayResponse pgPayment) {
        if (pgPayment.totalAmount() == null) {
            log.error("PG 결제 금액 누락: portonePaymentId={}", payment.getPortonePaymentId());
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PAID);
        }
        if (payment.getAmount().equals(pgPayment.totalAmount())) {
            return true;
        }
        log.error("결제 금액 불일치: portonePaymentId={}, 주문 금액={}, PG 금액={}",
                payment.getPortonePaymentId(), payment.getAmount(), pgPayment.totalAmount());
        return false;
    }

    // 웹훅에서 PG 결제를 되돌린다. 실패하면 예외가 그대로 전달돼 웹훅 재전송으로 다시 시도한다
    private void cancelPgPayment(String portonePaymentId, String reason) {
        paymentGateway.cancelPayment(portonePaymentId, reason, null);
    }

    private Payment getOwnedPayment(Long memberId, String portonePaymentId) {
        Payment payment = paymentService.getWithOrder(portonePaymentId);
        if (!payment.getOrder().getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return payment;
    }

    // confirm 응답은 금액 불일치로 끝나야 하므로 PG 취소 실패를 삼킨다.
    // 결제 실패는 이미 기록됐고, Transaction.Paid 웹훅이 FAILED + PG PAID 상태를 보고 다시 취소한다
    private void cancelPgPaymentQuietly(String portonePaymentId) {
        try {
            paymentGateway.cancelPayment(portonePaymentId, AMOUNT_MISMATCH_CANCEL_REASON, null);
        } catch (Exception e) {
            log.error("PG 자동 취소 실패, Paid 웹훅 보상 취소로 재시도 예정(웹훅 미수신 시 수동 처리 필요): portonePaymentId={}",
                    portonePaymentId, e);
        }
    }
}
