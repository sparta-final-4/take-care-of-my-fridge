package org.example.takecareofmyfridgebe.domain.forecast.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.forecast.service.ForecastPredictionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForecastPredictionScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ForecastPredictionService predictionService;

    // 서버가 여러 대여도 한 서버만 워커를 호출한다. 워커 응답 대기(최대 5분)보다 길게 잠근다.
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "forecastPrediction", lockAtMostFor = "30m", lockAtLeastFor = "5m")
    public void predictDueWeek() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate targetWeek = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDateTime dueAt = targetWeek.minusDays(1).atTime(22, 0);
        if (!now.isBefore(dueAt) && now.isBefore(targetWeek.atTime(6, 0))) {
            try {
                predictionService.generate(targetWeek, targetWeek.minusDays(1).atTime(18, 0));
            } catch (RuntimeException e) {
                log.error("주간 예측 생성 실패, 다음 실행에서 재시도: weekStart={}", targetWeek, e);
            }
        }
    }
}
