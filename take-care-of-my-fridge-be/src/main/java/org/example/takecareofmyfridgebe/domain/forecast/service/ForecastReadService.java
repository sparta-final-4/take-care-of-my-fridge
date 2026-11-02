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
    private final ForecastDiagnosticsService diagnosticsService;

    public ForecastResponse getLatest() {
        PredictionRun run = runRepository.findTopBySyntheticDataFalseOrderByTargetWeekStartDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORECAST_NOT_FOUND,
                        diagnosticsService.describe(diagnosticsService.latestDueWeek())));
        return toResponse(run);
    }

    public ForecastResponse getDemo() {
        PredictionRun run = runRepository.findTopBySyntheticDataTrueOrderByTargetWeekStartDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORECAST_NOT_FOUND));
        return toResponse(run);
    }

    public ForecastResponse getByWeek(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart는 월요일이어야 합니다.");
        }
        PredictionRun run = runRepository.findByTargetWeekStartAndSyntheticDataFalse(weekStart)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORECAST_NOT_FOUND,
                        diagnosticsService.describe(weekStart)));
        return toResponse(run);
    }

    private ForecastResponse toResponse(PredictionRun run) {
        LocalDate weekStart = run.getTargetWeekStart();
        LocalDate previous = null;
        LocalDate next = null;
        if (!run.isSyntheticData()) {
            previous = runRepository
                    .findTopBySyntheticDataFalseAndTargetWeekStartLessThanOrderByTargetWeekStartDesc(weekStart)
                    .map(PredictionRun::getTargetWeekStart).orElse(null);
            next = runRepository
                    .findTopBySyntheticDataFalseAndTargetWeekStartGreaterThanOrderByTargetWeekStartAsc(weekStart)
                    .map(PredictionRun::getTargetWeekStart).orElse(null);
        }
        return ForecastResponse.from(run,
                forecastRepository.findByPredictionRunIdOrderByProductIdAsc(run.getId()),
                previous, next);
    }
}
