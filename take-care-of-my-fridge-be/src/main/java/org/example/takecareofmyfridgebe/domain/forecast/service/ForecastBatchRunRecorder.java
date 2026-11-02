package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.domain.forecast.repository.ForecastBatchRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ForecastBatchRunRecorder {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ForecastBatchRunRepository repository;

    @Transactional
    public void record(LocalDate weekStart, ForecastBatchStatus status, String failureType) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        ForecastBatchRun run = repository.findByTargetWeekStart(weekStart).orElse(null);
        if (run == null) {
            repository.save(new ForecastBatchRun(weekStart, status, now, failureType));
        } else if (status != ForecastBatchStatus.ALREADY_EXISTS
                || run.getStatus() != ForecastBatchStatus.CREATED) {
            run.update(status, now, failureType);
        }
    }
}
