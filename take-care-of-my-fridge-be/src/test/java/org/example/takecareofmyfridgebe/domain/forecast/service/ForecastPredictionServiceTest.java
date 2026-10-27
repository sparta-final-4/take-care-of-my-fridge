package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ForecastPredictionServiceTest {

    private final PredictionRunRepository runs = mock(PredictionRunRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final NetSalesService sales = mock(NetSalesService.class);
    private final ForecastWorkerClient worker = mock(ForecastWorkerClient.class);
    private final ForecastPredictionSaveService saver = mock(ForecastPredictionSaveService.class);
    private final ForecastPredictionService service = new ForecastPredictionService(
            runs, products, sales, worker, saver);

    @Test
    void sendsRealHistoryToWorkerAndSavesPredictionWithCutoff() {
        LocalDate week = LocalDate.of(2026, 10, 26);
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 25, 18, 0);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.ON_SALE);
        when(products.findAll()).thenReturn(List.of(product));
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.empty());
        when(sales.getDailySales(week.minusDays(84), week.minusDays(1), cutoff))
                .thenReturn(List.of(new DailyNetSales(1L, week.minusDays(1), 4),
                        new DailyNetSales(1L, week.minusDays(7), 3)));
        var result = new ForecastWorkerClient.Result("weekly-mlp-v1",
                List.of(new ForecastWorkerClient.Prediction(1L, 9)));
        when(worker.predict(eq(week), anyList(), anyList())).thenReturn(result);

        service.generate(week, cutoff);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ForecastWorkerClient.ProductHistory>> inputs = ArgumentCaptor.forClass(List.class);
        verify(worker).predict(eq(week), anyList(), inputs.capture());
        assertThat(inputs.getValue()).hasSize(1);
        assertThat(inputs.getValue().get(0).history()).hasSize(28);
        assertThat(inputs.getValue().get(0).history().get(27)).isEqualTo(4);
        verify(saver).save(eq(week), eq(cutoff), eq(result),
                anyMap(), anyMap());
    }

    @Test
    void skipsExistingWeekWithoutCallingWorker() {
        LocalDate week = LocalDate.of(2026, 10, 26);
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.of(mock(PredictionRun.class)));

        service.generate(week, LocalDateTime.of(2026, 10, 25, 18, 0));

        verifyNoInteractions(sales, worker, saver);
    }

    @Test
    void doesNotCreateSyntheticForecastWhenThereAreNoSalesSamples() {
        LocalDate week = LocalDate.of(2026, 10, 26);
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 25, 18, 0);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.ON_SALE);
        when(products.findAll()).thenReturn(List.of(product));
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.empty());
        when(sales.getDailySales(week.minusDays(84), week.minusDays(1), cutoff))
                .thenReturn(List.of());

        service.generate(week, cutoff);

        verifyNoInteractions(worker, saver);
    }
}
