package org.example.takecareofmyfridgebe.domain.payment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.payment.facade.BillingKeyFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 회원이 삭제한 카드 중 PortOne 빌링키 삭제가 실패해 남은 것을 다시 지운다.
// 우리 쪽에서는 이미 삭제돼 결제에 쓰이지 않으므로 급하지 않아 1시간마다 돈다
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyPgDeleteScheduler {

    private final BillingKeyFacade billingKeyFacade;

    // 이전 실행이 끝나고 1시간 뒤 다시 실행한다. 서버가 여러 대여도 잠금을 잡은 한 대만 실행한다.
    // PortOne이 느려 한 건에 연결·응답 제한 시간(8초)을 다 써도 수백 건을 끝낼 수 있게 잠금을 실행 간격 안에서 길게 둔다
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    @SchedulerLock(name = "billingKeyPgDelete", lockAtMostFor = "55m", lockAtLeastFor = "1m")
    public void retryPgDeletes() {
        try {
            billingKeyFacade.retryPgDeletes();
        } catch (RuntimeException e) {
            log.error("PortOne 빌링키 삭제 재시도 실패, 다음 실행에서 재시도", e);
        }
    }
}
