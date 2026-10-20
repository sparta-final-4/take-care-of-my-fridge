package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ForecastReadServiceTest {

    private final PredictionRunRepository runs = mock(PredictionRunRepository.class);
    private final SalesForecastRepository forecasts = mock(SalesForecastRepository.class);
    private final ForecastReadService service = new ForecastReadService(runs, forecasts);

    @Test
    void readsRequestedWeekAndExistingNeighborWeeks() {
        LocalDate week = LocalDate.of(2026, 10, 19);
        PredictionRun current = run(3L, week);
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.of(current));
        when(runs.findTopByTargetWeekStartLessThanOrderByTargetWeekStartDesc(week))
                .thenReturn(Optional.of(run(2L, week.minusWeeks(2))));
        when(runs.findTopByTargetWeekStartGreaterThanOrderByTargetWeekStartAsc(week))
                .thenReturn(Optional.of(run(4L, week.plusWeeks(2))));
        when(forecasts.findByPredictionRunIdOrderByProductIdAsc(3L)).thenReturn(List.of());

        var response = service.getByWeek(week);

        assertThat(response.weekStart()).isEqualTo(week);
        assertThat(response.previousWeekStart()).isEqualTo(week.minusWeeks(2));
        assertThat(response.nextWeekStart()).isEqualTo(week.plusWeeks(2));
        assertThat(response.items()).isEmpty();
    }

    @Test
    void rejectsNonMondayBeforeQuerying() {
        assertThatThrownBy(() -> service.getByWeek(LocalDate.of(2026, 10, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(runs, forecasts);
    }

    @Test
    void missingWeekReturnsF001() {
        LocalDate week = LocalDate.of(2026, 10, 19);
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByWeek(week))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.FORECAST_NOT_FOUND));
        verifyNoInteractions(forecasts);
    }

    @Test
    void latestUsesMostRecentTargetWeek() {
        LocalDate latest = LocalDate.of(2026, 11, 2);
        PredictionRun run = run(8L, latest);
        when(runs.findTopByOrderByTargetWeekStartDesc()).thenReturn(Optional.of(run));
        when(forecasts.findByPredictionRunIdOrderByProductIdAsc(8L)).thenReturn(List.of());

        var response = service.getLatest();

        assertThat(response.weekStart()).isEqualTo(latest);
        verify(runs, never()).findByTargetWeekStart(latest);
    }

    @Test
    void latestReturnsF001WhenNoForecastExists() {
        when(runs.findTopByOrderByTargetWeekStartDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(service::getLatest)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.FORECAST_NOT_FOUND));
        verifyNoInteractions(forecasts);
    }
    private PredictionRun run(Long id, LocalDate week) {
        PredictionRun run = PredictionRun.builder().targetWeekStart(week)
                .modelVersion("weekly-mlp-v1")
                .dataCutoffAt(LocalDateTime.of(2026, 10, 18, 18, 0))
                .build();
        ReflectionTestUtils.setField(run, "id", id);
        return run;
    }
}
