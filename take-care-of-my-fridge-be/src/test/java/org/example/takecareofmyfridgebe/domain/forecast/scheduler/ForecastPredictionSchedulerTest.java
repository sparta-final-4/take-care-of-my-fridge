package org.example.takecareofmyfridgebe.domain.forecast.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.domain.forecast.service.ForecastBatchRunRecorder;
import org.example.takecareofmyfridgebe.domain.forecast.service.ForecastPredictionService;
import org.junit.jupiter.api.Test;

class ForecastPredictionSchedulerTest {

    private final ForecastPredictionService prediction = mock(ForecastPredictionService.class);
    private final ForecastBatchRunRecorder recorder = mock(ForecastBatchRunRecorder.class);
    private final ForecastPredictionScheduler scheduler = new ForecastPredictionScheduler(prediction, recorder);
    private final LocalDate week = LocalDate.of(2026, 11, 2);

    @Test
    void recordsMissingSamplesAfterBatchAttempt() {
        when(prediction.generate(week, week.minusDays(1).atTime(18, 0)))
                .thenReturn(ForecastBatchStatus.NO_TRAINING_SAMPLES);

        scheduler.runForWeek(week);

        verify(recorder).record(week, ForecastBatchStatus.NO_TRAINING_SAMPLES, null);
    }

    @Test
    void recordsFailureCategoryWithoutSensitiveMessage() {
        when(prediction.generate(week, week.minusDays(1).atTime(18, 0)))
                .thenThrow(new IllegalStateException("do not store this message"));

        scheduler.runForWeek(week);

        verify(recorder).record(week, ForecastBatchStatus.FAILED, "IllegalStateException");
    }
}
