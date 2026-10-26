package org.example.takecareofmyfridgebe.domain.payment.scheduler;

import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentTimeoutSchedulerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentFacade paymentFacade;

    @InjectMocks
    private PaymentTimeoutScheduler scheduler;

    private static Payment payment(long id) {
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(id);
        return payment;
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무것도 처리하지 않는다")
    void noExpiredPayments() {
        given(paymentService.findExpiredReady(any(), eq(0L), eq(PaymentTimeoutScheduler.BATCH_SIZE))).willReturn(List.of());

        scheduler.expireOverduePayments();

        verify(paymentFacade, never()).expire(any());
    }

    @Test
    @DisplayName("한 건 처리가 실패해도 나머지 건은 계속 처리한다")
    void oneFailure_othersStillProcessed() {
        Payment first = payment(1L);
        Payment second = payment(2L);
        Payment third = payment(3L);
        given(paymentService.findExpiredReady(any(), eq(0L), eq(PaymentTimeoutScheduler.BATCH_SIZE)))
                .willReturn(List.of(first, second, third));
        willThrow(new IllegalStateException("PG 오류")).given(paymentFacade).expire(second);

        scheduler.expireOverduePayments();

        verify(paymentFacade).expire(first);
        verify(paymentFacade).expire(third);
    }

    @Test
    @DisplayName("한 번에 다 못 읽으면 마지막 id 다음부터 이어서 읽어 남은 건을 처리한다")
    void fullBatch_readsNextPageAfterLastId() {
        List<Payment> fullPage = LongStream.rangeClosed(1, PaymentTimeoutScheduler.BATCH_SIZE)
                .mapToObj(PaymentTimeoutSchedulerTest::payment)
                .toList();
        Payment last = payment(500L);
        given(paymentService.findExpiredReady(any(), anyLong(), eq(PaymentTimeoutScheduler.BATCH_SIZE)))
                .willReturn(fullPage, List.of(last));

        scheduler.expireOverduePayments();

        verify(paymentService).findExpiredReady(any(), eq(0L), eq(PaymentTimeoutScheduler.BATCH_SIZE));
        verify(paymentService).findExpiredReady(any(), eq((long) PaymentTimeoutScheduler.BATCH_SIZE),
                eq(PaymentTimeoutScheduler.BATCH_SIZE));
        verify(paymentFacade).expire(last);
    }

    @Test
    @DisplayName("보상 취소 재시도는 한 건이 실패해도 나머지 건을 계속 시도한다")
    void retryCompensations_oneFailure_othersStillProcessed() {
        Payment first = payment(1L);
        Payment second = payment(2L);
        given(paymentService.findCompensationPending(0L, PaymentTimeoutScheduler.BATCH_SIZE))
                .willReturn(List.of(first, second));
        willThrow(new IllegalStateException("PG 오류")).given(paymentFacade).retryCompensation(first);

        scheduler.retryCompensations();

        verify(paymentFacade).retryCompensation(second);
    }
}
