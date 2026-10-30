package org.example.takecareofmyfridgebe.domain.refund.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.refund.facade.RefundFacade;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 요청 상태로 오래 남은 환불을 PG 조회로 마무리한다. PG에 취소 요청이 닿지 않았거나, 서버가 꺼져 취소 웹훅을 놓친 환불이 대상이다.
// 그대로 두면 그 주문은 새 환불을 신청할 수 없고 수량도 환불한 것으로 잡힌다
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconcileScheduler {

    // PG 취소 요청과 웹훅이 끝나기를 기다리는 시간. 이보다 오래된 요청 상태 환불만 정리한다
    static final long STALE_MINUTES = 10;

    private final RefundQueryService refundQueryService;
    private final RefundFacade refundFacade;

    // 이전 실행이 끝나고 5분 뒤 다시 실행해 실행끼리 겹치지 않는다. 서버가 여러 대여도 잠금을 잡은 한 대만 실행한다
    @Scheduled(fixedDelay = 300_000, initialDelay = 180_000)
    @SchedulerLock(name = "refundReconcile", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void reconcileStaleRefunds() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        for (String portonePaymentId : refundQueryService.findPaymentsWithRequestedBefore(staleBefore)) {
            reconcile(portonePaymentId, staleBefore);
        }
    }

    // 한 건이 실패해도 나머지는 계속 처리한다. 실패한 건은 요청 상태로 남아 다음 실행에서 다시 시도한다
    private void reconcile(String portonePaymentId, LocalDateTime staleBefore) {
        try {
            refundFacade.syncWithPg(portonePaymentId, PaymentChangedBy.SCHEDULER, staleBefore);
        } catch (RuntimeException e) {
            log.error("환불 정리 실패, 다음 실행에서 재시도: portonePaymentId={}", portonePaymentId, e);
        }
    }
}
