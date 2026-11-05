package org.example.takecareofmyfridgebe.domain.settlement.service;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.dto.PaymentMethodSalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.dto.PaymentSalesRow;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesAmountResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementMethodResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPaymentResponse;
import org.example.takecareofmyfridgebe.domain.settlement.repository.DailySalesRepository;
import org.example.takecareofmyfridgebe.domain.settlement.repository.SalesHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 4);

    @Mock
    private DailySalesRepository dailySalesRepository;

    @Mock
    private SalesHistoryRepository salesHistoryRepository;

    @InjectMocks
    private SettlementQueryService settlementQueryService;

    @Test
    @DisplayName("시작일부터 종료일까지 중 daily_sales 행이 없는 날만 날짜순으로 돌려준다")
    void findMissingDates_returnDatesWithoutRow() {
        given(dailySalesRepository.findSalesDatesBetween(START, END))
                .willReturn(List.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)));

        assertThat(settlementQueryService.findMissingDates(START, END))
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("모든 날이 집계돼 있으면 빈 목록이다")
    void findMissingDates_returnEmpty_whenAllAggregated() {
        given(dailySalesRepository.findSalesDatesBetween(START, END))
                .willReturn(START.datesUntil(END.plusDays(1)).toList());

        assertThat(settlementQueryService.findMissingDates(START, END)).isEmpty();
    }

    @Test
    @DisplayName("결제 이력 합계를 매출 금액으로 바꾸고 순매출을 계산한다")
    void getSales_mapSumToAmounts() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 30, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 30, 15, 0);
        given(salesHistoryRepository.sumSalesBetween(from, to)).willReturn(new DailySalesSum(10000L, 2L, 15000L, 1L));

        assertThat(settlementQueryService.getSales(from, to))
                .isEqualTo(new SalesAmountResponse(10000L, 15000L, -5000L, 2, 1));
    }

    @Test
    @DisplayName("결제 건별 목록은 1부터 센 페이지를 바꿔 읽고 순매출을 계산한다")
    void getPayments_mapRowsWithNetAmount() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 31, 0, 0);
        PageRequest pageable = PageRequest.of(1, 20);
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 5, 12, 0);
        given(salesHistoryRepository.findPaymentSalesBetween(from, to, pageable)).willReturn(new PageImpl<>(
                List.of(new PaymentSalesRow(41L, paidAt, "20261005-000001", "구매자", true, PgProvider.TOSSPAYMENTS,
                        0L, 3000L)), pageable, 21));

        PageResponse<SettlementPaymentResponse> response = settlementQueryService.getPayments(from, to, 2, 20);

        assertThat(response.content()).containsExactly(
                new SettlementPaymentResponse(41L, paidAt, "20261005-000001", "구매자", true, PgProvider.TOSSPAYMENTS,
                        0L, 3000L, -3000L));
        assertThat(response.pageNumber()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(21);
    }

    @Test
    @DisplayName("결제 수단별 합계는 결제금액이 큰 순으로, 같으면 등록 카드, 그다음 결제대행사 선언 순으로 주고 순매출을 계산한다")
    void getMethodSales_sortByPaidAmountAndMapNetAmount() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 31, 0, 0);
        given(salesHistoryRepository.sumSalesByMethodBetween(from, to)).willReturn(List.of(
                new PaymentMethodSalesSum(false, PgProvider.KAKAOPAY, 0L, 0L, 8000L, 1L),
                new PaymentMethodSalesSum(false, PgProvider.TOSSPAYMENTS, 20000L, 1L, 0L, 0L),
                new PaymentMethodSalesSum(false, PgProvider.KICC, 20000L, 1L, 0L, 0L),
                new PaymentMethodSalesSum(true, PgProvider.TOSSPAYMENTS, 20000L, 2L, 5000L, 1L)));

        assertThat(settlementQueryService.getMethodSales(from, to)).containsExactly(
                new SettlementMethodResponse(true, PgProvider.TOSSPAYMENTS, 20000L, 5000L, 15000L, 2, 1),
                new SettlementMethodResponse(false, PgProvider.KICC, 20000L, 0L, 20000L, 1, 0),
                new SettlementMethodResponse(false, PgProvider.TOSSPAYMENTS, 20000L, 0L, 20000L, 1, 0),
                new SettlementMethodResponse(false, PgProvider.KAKAOPAY, 0L, 8000L, -8000L, 0, 1));
    }
}
