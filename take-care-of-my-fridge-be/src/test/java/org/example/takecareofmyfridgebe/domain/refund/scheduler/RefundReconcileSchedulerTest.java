package org.example.takecareofmyfridgebe.domain.refund.scheduler;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.refund.facade.RefundFacade;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundReconcileSchedulerTest {

    @Mock
    private RefundQueryService refundQueryService;

    @Mock
    private RefundFacade refundFacade;

    @InjectMocks
    private RefundReconcileScheduler scheduler;

    @Test
    @DisplayName("10분 넘게 요청 상태인 환불의 결제를 같은 기준 시각으로 PG와 맞춘다")
    void reconcilesWithStaleDeadline() {
        LocalDateTime before = LocalDateTime.now();
        given(refundQueryService.findPaymentsWithRequestedBefore(any())).willReturn(List.of("payment-1"));

        scheduler.reconcileStaleRefunds();

        ArgumentCaptor<LocalDateTime> staleBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refundQueryService).findPaymentsWithRequestedBefore(staleBefore.capture());
        assertThat(staleBefore.getValue())
                .isBeforeOrEqualTo(LocalDateTime.now().minusMinutes(RefundReconcileScheduler.STALE_MINUTES))
                .isAfterOrEqualTo(before.minusMinutes(RefundReconcileScheduler.STALE_MINUTES));
        verify(refundFacade).syncWithPg("payment-1", PaymentChangedBy.SCHEDULER, staleBefore.getValue());
    }

    @Test
    @DisplayName("한 결제의 정리가 실패해도 나머지 결제는 계속 정리한다")
    void oneFailure_othersStillProcessed() {
        given(refundQueryService.findPaymentsWithRequestedBefore(any())).willReturn(List.of("payment-1", "payment-2"));
        willThrow(new IllegalStateException("PG 오류")).given(refundFacade)
                .syncWithPg(eq("payment-1"), eq(PaymentChangedBy.SCHEDULER), any());

        scheduler.reconcileStaleRefunds();

        verify(refundFacade).syncWithPg(eq("payment-2"), eq(PaymentChangedBy.SCHEDULER), any());
    }
}
