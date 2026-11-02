package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.domain.forecast.repository.ForecastBatchRunRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;

class ForecastDiagnosticsServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final NetSalesService sales = mock(NetSalesService.class);
    private final ForecastBatchRunRepository runs = mock(ForecastBatchRunRepository.class);
    private final ForecastDiagnosticsService service = new ForecastDiagnosticsService(products, sales, runs);
    private final LocalDate week = LocalDate.of(2026, 11, 2);

    @Test
    void explainsReadyInputsAndFailedBatch() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.ON_SALE);
        when(products.findAll()).thenReturn(List.of(product));
        when(sales.getDailySales(week.minusDays(84), week.minusDays(1),
                week.minusDays(1).atTime(18, 0)))
                .thenReturn(List.of(new DailyNetSales(1L, week.minusDays(1), 4)));
        when(runs.findByTargetWeekStart(week)).thenReturn(Optional.of(new ForecastBatchRun(week,
                ForecastBatchStatus.FAILED, LocalDateTime.of(2026, 11, 1, 22, 0),
                "ResourceAccessException")));

        String message = service.describe(week);

        assertThat(message).contains("예측 대상 상품: 1개 / 최소 1개 (충족)",
                "순판매 수량: 4개 (충족)", "학습 표본: 1개 / 최소 1개 (충족)",
                "실행 실패 (ResourceAccessException)");
    }

    @Test
    void explainsMissingProductsAndSamples() {
        when(products.findAll()).thenReturn(List.of());
        when(sales.getDailySales(week.minusDays(84), week.minusDays(1),
                week.minusDays(1).atTime(18, 0))).thenReturn(List.of());

        assertThat(service.describe(week)).contains("예측 대상 상품: 0개 / 최소 1개 (부족)",
                "순판매 수량: 0개 (부족)", "학습 표본: 0개 / 최소 1개 (부족)",
                "시도 기록 없음");
    }
}
