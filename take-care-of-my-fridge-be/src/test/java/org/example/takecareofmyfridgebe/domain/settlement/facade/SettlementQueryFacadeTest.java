package org.example.takecareofmyfridgebe.domain.settlement.facade;

import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesAmountResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesPeriodUnit;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPeriodResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;
import org.example.takecareofmyfridgebe.domain.settlement.service.SettlementQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SettlementQueryFacadeTest {

    // 2026-10-30은 금요일이다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 15, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Mock
    private SettlementQueryService settlementQueryService;

    @Mock
    private SettlementFacade settlementFacade;

    @InjectMocks
    private SettlementQueryFacade settlementQueryFacade;

    @Test
    @DisplayName("집계되지 않은 날을 먼저 집계한 뒤 기간의 일별 매출을 읽는다")
    void getSettlement_aggregatesMissingDatesBeforeReading() {
        LocalDate start = date(10, 1);
        LocalDate end = date(10, 3);
        given(settlementQueryService.findMissingDates(start, end)).willReturn(List.of(date(10, 1), date(10, 3)));
        given(settlementQueryService.getDailySales(start, end))
                .willReturn(List.of(daily(10, 1, 1000), daily(10, 2, 2000), daily(10, 3, 3000)));

        SettlementResponse response = settlementQueryFacade.getSettlement(request(SalesPeriodUnit.DAY, start, end), NOW);

        InOrder order = inOrder(settlementFacade, settlementQueryService);
        order.verify(settlementFacade).aggregate(date(10, 1));
        order.verify(settlementFacade).aggregate(date(10, 3));
        order.verify(settlementQueryService).getDailySales(start, end);
        assertThat(response.periods()).extracting(SettlementPeriodResponse::startDate)
                .containsExactly(date(10, 1), date(10, 2), date(10, 3));
        assertThat(response.summary()).isEqualTo(SalesAmountResponse.of(6000, 0, 3));
        verify(settlementQueryService, never()).getSales(any(), any());
    }

    @Test
    @DisplayName("기간에 오늘이 있으면 어제까지는 일별 매출을 읽고 오늘은 지금까지의 매출을 붙인다")
    void getSettlement_includesToday_appendsLiveSales() {
        LocalDate start = date(10, 28);
        given(settlementQueryService.findMissingDates(start, YESTERDAY)).willReturn(List.of());
        given(settlementQueryService.getDailySales(start, YESTERDAY))
                .willReturn(List.of(daily(10, 28, 1000), daily(10, 29, 2000)));
        given(settlementQueryService.getSales(TODAY.atStartOfDay(), NOW)).willReturn(SalesAmountResponse.of(500, 800, 1));

        SettlementResponse response = settlementQueryFacade.getSettlement(request(SalesPeriodUnit.DAY, start, TODAY), NOW);

        assertThat(response.periods()).containsExactly(
                SettlementPeriodResponse.of(date(10, 28), date(10, 28), SalesAmountResponse.of(1000, 0, 1)),
                SettlementPeriodResponse.of(date(10, 29), date(10, 29), SalesAmountResponse.of(2000, 0, 1)),
                SettlementPeriodResponse.of(TODAY, TODAY, SalesAmountResponse.of(500, 800, 1)));
        assertThat(response.summary()).isEqualTo(SalesAmountResponse.of(3500, 800, 3));
    }

    @Test
    @DisplayName("오늘만 조회하면 daily_sales는 보지 않고 지금까지의 매출만 센다")
    void getSettlement_onlyToday_skipsDailySales() {
        given(settlementQueryService.getSales(TODAY.atStartOfDay(), NOW)).willReturn(SalesAmountResponse.of(500, 0, 1));

        SettlementResponse response = settlementQueryFacade.getSettlement(request(SalesPeriodUnit.DAY, TODAY, TODAY), NOW);

        assertThat(response.periods()).containsExactly(
                SettlementPeriodResponse.of(TODAY, TODAY, SalesAmountResponse.of(500, 0, 1)));
        verify(settlementQueryService, never()).findMissingDates(any(), any());
        verify(settlementQueryService, never()).getDailySales(any(), any());
        verifyNoInteractions(settlementFacade);
    }

    @Test
    @DisplayName("주 단위는 월~일로 묶고, 기간에서 잘린 주는 기간 안의 날만 더한다")
    void getSettlement_week_groupsMondayToSundayWithinRange() {
        // 10/7(수) ~ 10/14(수): 10/5 주는 10/7~10/11, 10/12 주는 10/12~10/14만 들어온다
        LocalDate start = date(10, 7);
        LocalDate end = date(10, 14);
        given(settlementQueryService.findMissingDates(start, end)).willReturn(List.of());
        given(settlementQueryService.getDailySales(start, end)).willReturn(start.datesUntil(end.plusDays(1))
                .map(day -> daily(day.getMonthValue(), day.getDayOfMonth(), 1000))
                .toList());

        SettlementResponse response = settlementQueryFacade.getSettlement(request(SalesPeriodUnit.WEEK, start, end), NOW);

        assertThat(response.periods()).containsExactly(
                SettlementPeriodResponse.of(date(10, 7), date(10, 11), SalesAmountResponse.of(5000, 0, 5)),
                SettlementPeriodResponse.of(date(10, 12), date(10, 14), SalesAmountResponse.of(3000, 0, 3)));
        assertThat(response.summary()).isEqualTo(SalesAmountResponse.of(8000, 0, 8));
    }

    @Test
    @DisplayName("월 단위는 1일~말일로 묶고, 오늘이 든 달에는 오늘 지금까지의 매출도 더한다")
    void getSettlement_month_groupsCalendarMonthIncludingToday() {
        LocalDate start = date(9, 15);
        given(settlementQueryService.findMissingDates(start, YESTERDAY)).willReturn(List.of());
        given(settlementQueryService.getDailySales(start, YESTERDAY))
                .willReturn(List.of(daily(9, 29, 1000), daily(9, 30, 2000), daily(10, 1, 4000), daily(10, 29, 8000)));
        given(settlementQueryService.getSales(TODAY.atStartOfDay(), NOW)).willReturn(SalesAmountResponse.of(500, 300, 1));

        SettlementResponse response = settlementQueryFacade.getSettlement(request(SalesPeriodUnit.MONTH, start, TODAY), NOW);

        assertThat(response.periods()).containsExactly(
                SettlementPeriodResponse.of(date(9, 15), date(9, 30), SalesAmountResponse.of(3000, 0, 2)),
                SettlementPeriodResponse.of(date(10, 1), TODAY, SalesAmountResponse.of(12500, 300, 3)));
        assertThat(response.summary()).isEqualTo(SalesAmountResponse.of(15500, 300, 5));
    }

    private SettlementSearchRequest request(SalesPeriodUnit unit, LocalDate start, LocalDate end) {
        return new SettlementSearchRequest(unit, start, end);
    }

    private LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    private DailySales daily(int month, int day, long paidAmount) {
        return DailySales.builder().salesDate(date(month, day)).paidAmount(paidAmount).refundedAmount(0L)
                .paidCount(1).build();
    }
}
