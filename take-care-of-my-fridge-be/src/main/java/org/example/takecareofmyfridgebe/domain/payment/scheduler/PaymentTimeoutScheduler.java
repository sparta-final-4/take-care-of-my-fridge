package org.example.takecareofmyfridgebe.domain.payment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 결제창을 띄운 뒤 만료 시각까지 결제하지 않은 결제 시도를 실패 처리한다. 주문 취소·재고 복구는 주문 쪽이 맡는다.
// 받지 않은 결제의 PG 보상 취소가 실패해 남아 있으면 다시 시도한다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    static final int BATCH_SIZE = 100;

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;

    // 이전 실행이 끝나고 1분 뒤 다시 실행해 실행끼리 겹치지 않는다. 기동 직후에는 1분 기다린다
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireOverduePayments() {
        LocalDateTime now = LocalDateTime.now();
        long afterId = 0;
        List<Payment> batch;
        do {
            batch = paymentService.findExpiredReady(now, afterId, BATCH_SIZE);
            for (Payment payment : batch) {
                expire(payment);
                afterId = payment.getId();
            }
        } while (batch.size() == BATCH_SIZE);
    }

    // 웹훅 재전송이 끝났거나 서버가 꺼져 있어 놓친 보상 취소를 5분마다 다시 시도한다
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void retryCompensations() {
        long afterId = 0;
        List<Payment> batch;
        do {
            batch = paymentService.findCompensationPending(afterId, BATCH_SIZE);
            for (Payment payment : batch) {
                retryCompensation(payment);
                afterId = payment.getId();
            }
        } while (batch.size() == BATCH_SIZE);
    }

    // 한 건이 실패해도 나머지는 계속 처리한다. 실패한 건은 READY로 남아 다음 실행에서 다시 시도한다
    private void expire(Payment payment) {
        try {
            paymentFacade.expire(payment);
        } catch (RuntimeException e) {
            log.error("결제 만료 처리 실패, 다음 실행에서 재시도: portonePaymentId={}", payment.getPortonePaymentId(), e);
        }
    }

    // 실패한 건은 보상 취소 기록이 남지 않아 다음 실행에서 다시 시도한다
    private void retryCompensation(Payment payment) {
        try {
            paymentFacade.retryCompensation(payment);
        } catch (RuntimeException e) {
            log.error("보상 취소 재시도 실패, 다음 실행에서 재시도: portonePaymentId={}", payment.getPortonePaymentId(), e);
        }
    }
}
