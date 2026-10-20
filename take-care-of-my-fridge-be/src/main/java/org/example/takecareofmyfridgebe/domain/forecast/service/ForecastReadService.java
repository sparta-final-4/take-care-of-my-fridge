package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.dto.ForecastResponse;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForecastReadService {

    private final PredictionRunRepository runRepository;
    private final SalesForecastRepository forecastRepository;

    public ForecastResponse getLatest() {
        PredictionRun run = runRepository.findTopByOrderByTargetWeekStartDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORECAST_NOT_FOUND));
        return toResponse(run);
    }

    public ForecastResponse getByWeek(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart는 월요일이어야 합니다.");
        }
        PredictionRun run = runRepository.findByTargetWeekStart(weekStart)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORECAST_NOT_FOUND));
        return toResponse(run);
    }

    private ForecastResponse toResponse(PredictionRun run) {
        LocalDate weekStart = run.getTargetWeekStart();
        LocalDate previous = runRepository
                .findTopByTargetWeekStartLessThanOrderByTargetWeekStartDesc(weekStart)
                .map(PredictionRun::getTargetWeekStart).orElse(null);
        LocalDate next = runRepository
                .findTopByTargetWeekStartGreaterThanOrderByTargetWeekStartAsc(weekStart)
                .map(PredictionRun::getTargetWeekStart).orElse(null);
        return ForecastResponse.from(run,
                forecastRepository.findByPredictionRunIdOrderByProductIdAsc(run.getId()),
                previous, next);
    }
}
