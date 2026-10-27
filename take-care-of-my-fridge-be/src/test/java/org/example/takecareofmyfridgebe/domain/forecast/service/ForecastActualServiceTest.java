package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ForecastActualServiceTest {

    private final PredictionRunRepository runs = mock(PredictionRunRepository.class);
    private final SalesForecastRepository forecasts = mock(SalesForecastRepository.class);
    private final NetSalesService netSales = mock(NetSalesService.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ForecastActualService service = new ForecastActualService(runs, forecasts, netSales, products);

    @Test
    void confirmsActualsAndAddsNewTopSellerOnlyOnce() {
        LocalDate week = LocalDate.of(2026, 10, 19);
        LocalDateTime asOf = LocalDateTime.of(2026, 10, 26, 6, 0);
        PredictionRun run = PredictionRun.builder().targetWeekStart(week)
                .modelVersion("weekly-mlp-v1").dataCutoffAt(week.minusDays(1).atStartOfDay()).build();
        ReflectionTestUtils.setField(run, "id", 7L);
        SalesForecast predicted = SalesForecast.builder().predictionRun(run).productId(1L)
                .productName("기존").predictedRank(1).predictedQty(10).baselineQty(8).build();
        Product entrant = mock(Product.class);
        when(entrant.getId()).thenReturn(2L);
        when(entrant.getName()).thenReturn("신규");
        when(runs.findByTargetWeekStartForUpdate(week)).thenReturn(Optional.of(run));
        when(forecasts.findByPredictionRunIdOrderByProductIdAsc(7L)).thenReturn(List.of(predicted));
        when(netSales.getDailySales(week, week.plusDays(6), asOf)).thenReturn(List.of(
                new DailyNetSales(1L, week, 2), new DailyNetSales(2L, week, 5)));
        when(products.findAllById(List.of(2L))).thenReturn(List.of(entrant));

        service.confirmWeek(week, asOf);
        service.confirmWeek(week, asOf);

        assertThat(predicted.getActualQty()).isEqualTo(2);
        assertThat(predicted.getActualRank()).isEqualTo(2);
        assertThat(run.getConfirmedAt()).isEqualTo(asOf);
        verify(forecasts).saveAll(anyList());
    }

    @Test
    void doesNothingWhenForecastRunDoesNotExist() {
        LocalDate week = LocalDate.of(2026, 10, 19);
        when(runs.findByTargetWeekStartForUpdate(week)).thenReturn(Optional.empty());

        service.confirmWeek(week, LocalDateTime.of(2026, 10, 26, 6, 0));

        verifyNoInteractions(netSales, forecasts, products);
    }
}
