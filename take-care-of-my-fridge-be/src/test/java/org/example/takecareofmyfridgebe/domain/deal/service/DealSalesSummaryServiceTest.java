package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.junit.jupiter.api.Test;

class DealSalesSummaryServiceTest {

    private final DealSalesInputService salesInputService = mock(DealSalesInputService.class);
    private final DealSalesSummaryService service = new DealSalesSummaryService(salesInputService);

    @Test
    void sumsConfirmedNetUnitsAndAveragesAcrossFourDaysIncludingMissingDays() {
        LocalDate weekStart = LocalDate.of(2026, 11, 2);
        LocalDateTime asOf = LocalDateTime.of(2026, 11, 1, 18, 0);
        when(salesInputService.getSameWeekdaySales(weekStart, DayOfWeek.WEDNESDAY, asOf))
                .thenReturn(List.of(
                        new DailyProductSales(1L, LocalDate.of(2026, 10, 7), 2),
                        new DailyProductSales(1L, LocalDate.of(2026, 10, 14), 3),
                        new DailyProductSales(2L, LocalDate.of(2026, 10, 21), 6)));

        assertThat(service.getByProduct(weekStart, DayOfWeek.WEDNESDAY, asOf))
                .isEqualTo(Map.of(1L, new DealSalesSummary(5, 1),
                        2L, new DealSalesSummary(6, 2)));
    }
}
