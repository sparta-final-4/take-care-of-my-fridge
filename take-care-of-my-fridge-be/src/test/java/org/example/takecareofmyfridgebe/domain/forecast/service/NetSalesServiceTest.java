package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.repository.ForecastSalesRepository;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.junit.jupiter.api.Test;

class NetSalesServiceTest {

    private final ForecastSalesRepository repository = mock(ForecastSalesRepository.class);
    private final NetSalesService service = new NetSalesService(repository);

    @Test
    void subtractsOnlyCompletedRefundRowsReturnedForCutoff() {
        LocalDate day = LocalDate.of(2026, 10, 19);
        LocalDateTime asOf = LocalDateTime.of(2026, 10, 26, 6, 0);
        when(repository.findConfirmedSales(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), asOf))
                .thenReturn(List.of(new DailyNetSales(1L, day, 5), new DailyNetSales(2L, day, 3)));
        when(repository.findCompletedRefunds(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                asOf, RefundStatus.COMPLETED))
                .thenReturn(List.of(new DailyNetSales(1L, day, 2)));

        assertThat(service.getDailySales(day, day, asOf)).containsExactly(
                new DailyNetSales(1L, day, 3), new DailyNetSales(2L, day, 3));
    }
}
