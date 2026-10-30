package org.example.takecareofmyfridgebe.domain.review.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.review.facade.ReviewSummaryFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.review-summary.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewSummaryScheduler {

    /**
     * 새벽 실행의 시간 예산(25분). lockAtMostFor(30분)보다 짧아야 한다.
     */
    private static final Duration TIME_BUDGET = Duration.ofMinutes(25);

    private final ReviewSummaryFacade reviewSummaryFacade;

    /**
     * 새벽 정기 레시피 후기 요약 스케줄러.
     * ShedLock을 통해 다중 서버 환경에서도 단 하나의 인스턴스에서만 실행되도록 잠금을 획득한다.
     * Redis 장애 등으로 잠금을 획득하지 못하면 실행되지 않는다.
     */
    @Scheduled(cron = "${app.review-summary.cron}", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "reviewSummary",
            lockAtMostFor = "30m",
            lockAtLeastFor = "5m"
    )
    public void runScheduledReviewSummary() {
        log.info("[ShedLock] 락 획득 성공! 레시피 후기 요약 새벽 작업을 시작합니다.");
        reviewSummaryFacade.runOnce(TIME_BUDGET);
        log.info("[ShedLock] 레시피 후기 요약 새벽 작업 완료!");
    }
}
