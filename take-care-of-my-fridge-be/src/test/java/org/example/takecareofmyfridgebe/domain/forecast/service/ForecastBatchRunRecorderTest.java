package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.domain.forecast.repository.ForecastBatchRunRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ForecastBatchRunRecorderTest {

    private final ForecastBatchRunRepository repository = mock(ForecastBatchRunRepository.class);
    private final ForecastBatchRunRecorder recorder = new ForecastBatchRunRecorder(repository);
    private final LocalDate week = LocalDate.of(2026, 11, 2);

    @Test
    void storesFailureTypeWithoutExceptionMessage() {
        recorder.record(week, ForecastBatchStatus.FAILED, "ResourceAccessException");

        ArgumentCaptor<ForecastBatchRun> saved = ArgumentCaptor.forClass(ForecastBatchRun.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ForecastBatchStatus.FAILED);
        assertThat(saved.getValue().getFailureType()).isEqualTo("ResourceAccessException");
    }

    @Test
    void laterAlreadyExistsCheckDoesNotOverwriteSuccess() {
        ForecastBatchRun run = new ForecastBatchRun(week, ForecastBatchStatus.CREATED,
                LocalDateTime.of(2026, 11, 1, 22, 0), null);
        when(repository.findByTargetWeekStart(week)).thenReturn(Optional.of(run));

        recorder.record(week, ForecastBatchStatus.ALREADY_EXISTS, null);

        assertThat(run.getStatus()).isEqualTo(ForecastBatchStatus.CREATED);
        assertThat(run.getAttemptedAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 22, 0));
    }
}
