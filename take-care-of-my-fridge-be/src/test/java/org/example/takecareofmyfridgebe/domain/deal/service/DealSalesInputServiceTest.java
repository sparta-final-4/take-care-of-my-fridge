package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.service.OrderSalesService;
import org.junit.jupiter.api.Test;

class DealSalesInputServiceTest {

    private final OrderSalesService orderSalesService = mock(OrderSalesService.class);
    private final DealSalesInputService service = new DealSalesInputService(orderSalesService);

    @Test
    void readsFourPreviousWeeksAndKeepsOnlyTargetWeekday() {
        LocalDate weekStart = LocalDate.of(2026, 11, 2);
        LocalDateTime asOf = LocalDateTime.of(2026, 11, 1, 18, 0);
        LocalDate from = weekStart.minusWeeks(4);
        LocalDate to = weekStart.minusDays(1);
        DailyProductSales firstWednesday = new DailyProductSales(1L, LocalDate.of(2026, 10, 7), 3);
        DailyProductSales secondWednesday = new DailyProductSales(1L, LocalDate.of(2026, 10, 14), 2);
        when(orderSalesService.getDailyProductSales(from, to, asOf)).thenReturn(List.of(
                new DailyProductSales(1L, LocalDate.of(2026, 10, 5), 5),
                firstWednesday,
                secondWednesday,
                new DailyProductSales(2L, LocalDate.of(2026, 10, 21), 1)));

        assertThat(service.getSameWeekdaySales(weekStart, DayOfWeek.WEDNESDAY, asOf))
                .containsExactly(firstWednesday, secondWednesday,
                        new DailyProductSales(2L, LocalDate.of(2026, 10, 21), 1));
    }
}
