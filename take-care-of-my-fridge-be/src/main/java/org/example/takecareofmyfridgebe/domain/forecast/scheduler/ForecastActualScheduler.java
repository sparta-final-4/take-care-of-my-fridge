package org.example.takecareofmyfridgebe.domain.forecast.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.forecast.service.ForecastActualService;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForecastActualScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ForecastActualService actualService;
    private final PredictionRunRepository runRepository;

    // 서버가 여러 대여도 한 서버만 실적을 확정한다.
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "forecastActual", lockAtMostFor = "30m", lockAtLeastFor = "5m")
    public void confirmDueWeeks() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate latestCompletedWeek = now.toLocalDate().minusWeeks(1);
        runRepository.findByConfirmedAtIsNullAndTargetWeekStartLessThanEqualOrderByTargetWeekStartAsc(
                latestCompletedWeek).forEach(run -> {
                    LocalDate week = run.getTargetWeekStart();
                    LocalDateTime cutoff = week.plusWeeks(1).atTime(6, 0);
                    if (!now.isBefore(cutoff)) {
                        try {
                            actualService.confirmWeek(week, cutoff);
                        } catch (RuntimeException e) {
                            log.error("실제 판매량 확정 실패, 다음 실행에서 재시도: weekStart={}", week, e);
                        }
                    }
                });
    }
}
