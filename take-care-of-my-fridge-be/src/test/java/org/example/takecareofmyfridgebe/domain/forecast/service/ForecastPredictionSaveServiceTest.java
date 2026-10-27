package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ForecastPredictionSaveServiceTest {

    private final PredictionRunRepository runs = mock(PredictionRunRepository.class);
    private final SalesForecastRepository forecasts = mock(SalesForecastRepository.class);
    private final ForecastPredictionSaveService service = new ForecastPredictionSaveService(runs, forecasts);

    @Test
    void savesRealForecastWithRanksAndBaselines() {
        LocalDate week = LocalDate.of(2026, 10, 26);
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 25, 18, 0);
        Product first = mock(Product.class);
        Product second = mock(Product.class);
        when(first.getId()).thenReturn(1L);
        when(first.getName()).thenReturn("두부");
        when(second.getId()).thenReturn(2L);
        when(second.getName()).thenReturn("우유");
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.empty());
        when(runs.save(any(PredictionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = new ForecastWorkerClient.Result("weekly-mlp-v1", List.of(
                new ForecastWorkerClient.Prediction(1L, 3),
                new ForecastWorkerClient.Prediction(2L, 8)));

        service.save(week, cutoff, result, Map.of(1L, first, 2L, second), Map.of(1L, 2L, 2L, 5L));

        ArgumentCaptor<PredictionRun> run = ArgumentCaptor.forClass(PredictionRun.class);
        verify(runs).save(run.capture());
        assertThat(run.getValue().isSyntheticData()).isFalse();
        assertThat(run.getValue().getDataCutoffAt()).isEqualTo(cutoff);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SalesForecast>> rows = ArgumentCaptor.forClass(List.class);
        verify(forecasts).saveAll(rows.capture());
        assertThat(rows.getValue()).extracting(SalesForecast::getProductId).containsExactly(2L, 1L);
        assertThat(rows.getValue()).extracting(SalesForecast::getPredictedRank).containsExactly(1, 2);
        assertThat(rows.getValue()).extracting(SalesForecast::getBaselineQty).containsExactly(5, 2);
    }
}
