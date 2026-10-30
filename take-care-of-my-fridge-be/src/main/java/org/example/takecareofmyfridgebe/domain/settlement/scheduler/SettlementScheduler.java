package org.example.takecareofmyfridgebe.domain.settlement.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.settlement.facade.SettlementFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

// 매일 00:10(한국 시간)에 어제 하루의 매출을 집계해 daily_sales에 저장한다.
// 0시를 조금 넘겨 돌려 자정 직전에 시작한 결제·환불 트랜잭션이 커밋된 뒤 센다
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SettlementFacade settlementFacade;

    // 서버가 여러 대여도 한 대만 실행한다. 다시 돌려도 결과가 같으므로 잠금을 못 얻어 건너뛰어도 조회 때 채워진다
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "dailySalesAggregation", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void aggregateYesterday() {
        aggregateYesterday(LocalDate.now(ZONE));
    }

    // 실패해도 빈 날로 남을 뿐이며, 정산 조회가 빈 날을 그 자리에서 집계해 채운다
    void aggregateYesterday(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        try {
            settlementFacade.aggregate(yesterday);
        } catch (RuntimeException e) {
            log.error("일별 매출 집계 실패, 정산 조회 때 다시 집계: salesDate={}", yesterday, e);
        }
    }
}
