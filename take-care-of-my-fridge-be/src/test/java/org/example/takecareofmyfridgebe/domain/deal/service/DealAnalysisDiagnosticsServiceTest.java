package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;

class DealAnalysisDiagnosticsServiceTest {

    private final DealTrainingObservationService observations = mock(DealTrainingObservationService.class);
    private final DealProductInputService products = mock(DealProductInputService.class);
    private final DealAnalysisDiagnosticsService service =
            new DealAnalysisDiagnosticsService(observations, products);

    @Test
    void reportsMissingTrainingConditionsAndCurrentProducts() {
        when(observations.getObservations(any(LocalDateTime.class))).thenReturn(List.of(
                new DealDiscountModel.Observation(20, 10, 12),
                new DealDiscountModel.Observation(20, 10, 13)));
        when(products.getProducts()).thenReturn(List.of(
                new DealProductInput(1L, "재고 충분", 1000, ProductStatus.ON_SALE, 50),
                new DealProductInput(2L, "재고 부족", 1000, ProductStatus.ON_SALE, 49),
                new DealProductInput(3L, "판매 중지", 1000, ProductStatus.STOPPED, 100)));

        assertThat(service.describeMissing()).contains(
                "분석 대상 상품: 2개", "재고 기준 상품: 1개 (50개 이상)",
                "학습 가능한 종료 일반 특가: 2건 / 최소 3건 (부족)",
                "학습 할인율 종류: 1개 / 최소 2개 (부족)");
    }

    @Test
    void reportsReadyTrainingConditions() {
        when(observations.getObservations(any(LocalDateTime.class))).thenReturn(List.of(
                new DealDiscountModel.Observation(10, 10, 12),
                new DealDiscountModel.Observation(20, 10, 13),
                new DealDiscountModel.Observation(20, 10, 14)));
        when(products.getProducts()).thenReturn(List.of());

        assertThat(service.describeMissing()).contains(
                "학습 가능한 종료 일반 특가: 3건 / 최소 3건 (충족)",
                "학습 할인율 종류: 2개 / 최소 2개 (충족)");
    }
}
