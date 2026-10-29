package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickMetricService;
import org.junit.jupiter.api.Test;

class DealClickInputServiceTest {

    private final SearchClickMetricService metricService = mock(SearchClickMetricService.class);
    private final DealClickInputService service = new DealClickInputService(metricService);

    @Test
    void sumsOnlyTheSameWeekdayInTheFourPreviousWeeks() {
        LocalDate weekStart = LocalDate.of(2026, 11, 2);
        LocalDateTime asOf = LocalDateTime.of(2026, 11, 1, 18, 0);
        LocalDate[] days = {
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 7)};
        when(metricService.getProductClicks(days[0], days[0], asOf))
                .thenReturn(List.of(new ProductClickCount(1L, 3)));
        when(metricService.getProductClicks(days[1], days[1], asOf))
                .thenReturn(List.of(new ProductClickCount(1L, 2), new ProductClickCount(2L, 1)));
        when(metricService.getProductClicks(days[2], days[2], asOf)).thenReturn(List.of());
        when(metricService.getProductClicks(days[3], days[3], asOf))
                .thenReturn(List.of(new ProductClickCount(2L, 4)));

        assertThat(service.getSameWeekdayClicks(weekStart, DayOfWeek.WEDNESDAY, asOf))
                .isEqualTo(Map.of(1L, 5L, 2L, 5L));
        for (LocalDate day : days) {
            verify(metricService).getProductClicks(day, day, asOf);
        }
        verifyNoMoreInteractions(metricService);
    }
}
