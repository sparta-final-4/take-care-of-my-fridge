package org.example.takecareofmyfridgebe.domain.settlement.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesAmountResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesPeriodUnit;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPaymentResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPaymentSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPeriodResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.dto.TodaySalesResponse;
import org.example.takecareofmyfridgebe.domain.settlement.service.SettlementQueryService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 정산 조회. 빈 날을 채우는 집계가 각자 새 트랜잭션에서 커밋된 뒤 다시 읽도록 이 계층은 트랜잭션을 열지 않는다
@Component
@RequiredArgsConstructor
public class SettlementQueryFacade {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SettlementQueryService settlementQueryService;
    private final SettlementFacade settlementFacade;

    public SettlementResponse getSettlement(SettlementSearchRequest request) {
        return getSettlement(request, LocalDateTime.now(ZONE));
    }

    // 어제까지는 daily_sales를 읽고(빈 날은 먼저 집계해 채운다), 오늘은 지금까지의 매출을 바로 세어 붙인다
    SettlementResponse getSettlement(SettlementSearchRequest request, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        LocalDate lastClosedDate = endDate.isBefore(today) ? endDate : today.minusDays(1);

        List<DailyAmount> days = new ArrayList<>();
        if (!startDate.isAfter(lastClosedDate)) {
            settlementQueryService.findMissingDates(startDate, lastClosedDate).forEach(settlementFacade::aggregate);
            settlementQueryService.getDailySales(startDate, lastClosedDate).forEach(dailySales -> days.add(
                    new DailyAmount(dailySales.getSalesDate(), SalesAmountResponse.from(dailySales))));
        }
        if (!endDate.isBefore(today)) {
            days.add(new DailyAmount(today, settlementQueryService.getSales(today.atStartOfDay(), now)));
        }
        return group(request.unit(), startDate, endDate, days);
    }

    // 건별 조회는 결제 이력을 바로 읽으므로 빈 날 집계가 필요 없다. 오늘이 들어 있으면 지금까지의 결제·환불이 나온다
    public PageResponse<SettlementPaymentResponse> getPayments(SettlementPaymentSearchRequest request) {
        return settlementQueryService.getPayments(request.startDate().atStartOfDay(),
                request.endDate().plusDays(1).atStartOfDay(), request.page(), request.size());
    }

    public TodaySalesResponse getTodaySales() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        return new TodaySalesResponse(today, settlementQueryService.getSales(today.atStartOfDay(), now));
    }

    // 주는 월~일, 월은 1일~말일로 묶는다. 조회 기간이 주·월 중간에서 잘리면 기간 안의 날만 더하고 그 날짜를 내려준다
    private SettlementResponse group(SalesPeriodUnit unit, LocalDate startDate, LocalDate endDate,
                                     List<DailyAmount> days) {
        Map<LocalDate, List<SalesAmountResponse>> byPeriodStart = new LinkedHashMap<>();
        for (DailyAmount day : days) {
            byPeriodStart.computeIfAbsent(periodStart(unit, day.date()), key -> new ArrayList<>()).add(day.sales());
        }

        List<SettlementPeriodResponse> periods = byPeriodStart.entrySet().stream()
                .map(entry -> SettlementPeriodResponse.of(
                        max(entry.getKey(), startDate),
                        min(periodEnd(unit, entry.getKey()), endDate),
                        sum(entry.getValue())))
                .toList();
        return new SettlementResponse(sum(days.stream().map(DailyAmount::sales).toList()), periods);
    }

    private LocalDate periodStart(SalesPeriodUnit unit, LocalDate date) {
        return switch (unit) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private LocalDate periodEnd(SalesPeriodUnit unit, LocalDate periodStart) {
        return switch (unit) {
            case DAY -> periodStart;
            case WEEK -> periodStart.plusDays(6);
            case MONTH -> periodStart.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private SalesAmountResponse sum(List<SalesAmountResponse> sales) {
        long paidAmount = 0;
        long refundedAmount = 0;
        int paidCount = 0;
        int refundedCount = 0;
        for (SalesAmountResponse sale : sales) {
            paidAmount += sale.paidAmount();
            refundedAmount += sale.refundedAmount();
            paidCount += sale.paidCount();
            refundedCount += sale.refundedCount();
        }
        return SalesAmountResponse.of(paidAmount, refundedAmount, paidCount, refundedCount);
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private record DailyAmount(LocalDate date, SalesAmountResponse sales) {
    }
}
