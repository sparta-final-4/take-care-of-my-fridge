package org.example.takecareofmyfridgebe.domain.settlement.service;

import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;
import org.example.takecareofmyfridgebe.domain.settlement.repository.DailySalesRepository;
import org.example.takecareofmyfridgebe.domain.settlement.repository.SalesHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementCommandServiceTest {

    private static final LocalDate SALES_DATE = LocalDate.of(2026, 10, 28);
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 28, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 29, 0, 0);

    @Mock
    private SalesHistoryRepository salesHistoryRepository;

    @Mock
    private DailySalesRepository dailySalesRepository;

    @InjectMocks
    private SettlementCommandService settlementCommandService;

    @BeforeEach
    void setUp() {
        given(salesHistoryRepository.sumSalesBetween(FROM, TO)).willReturn(new DailySalesSum(50000L, 3L, 70000L));
    }

    @Test
    @DisplayName("집계한 적 없는 날이면 그날 0시부터 다음 날 0시 전까지의 합계로 새 행을 만든다")
    void aggregate_newDate_savesRow() {
        given(dailySalesRepository.findBySalesDate(SALES_DATE)).willReturn(Optional.empty());

        settlementCommandService.aggregate(SALES_DATE);

        ArgumentCaptor<DailySales> captor = ArgumentCaptor.forClass(DailySales.class);
        verify(dailySalesRepository).saveAndFlush(captor.capture());
        DailySales saved = captor.getValue();
        assertThat(saved.getSalesDate()).isEqualTo(SALES_DATE);
        assertThat(saved.getPaidAmount()).isEqualTo(50000L);
        assertThat(saved.getRefundedAmount()).isEqualTo(70000L);
        assertThat(saved.getPaidCount()).isEqualTo(3);
        assertThat(saved.getNetAmount()).isEqualTo(-20000L);
    }

    @Test
    @DisplayName("이미 집계한 날이면 새로 계산한 합계로 바꾸고 행을 새로 만들지 않는다")
    void aggregate_existingDate_reaggregates() {
        DailySales existing = DailySales.builder().salesDate(SALES_DATE)
                .paidAmount(10000L).refundedAmount(0L).paidCount(1).build();
        given(dailySalesRepository.findBySalesDate(SALES_DATE)).willReturn(Optional.of(existing));

        settlementCommandService.aggregate(SALES_DATE);

        assertThat(existing.getPaidAmount()).isEqualTo(50000L);
        assertThat(existing.getRefundedAmount()).isEqualTo(70000L);
        assertThat(existing.getPaidCount()).isEqualTo(3);
        verify(dailySalesRepository, never()).saveAndFlush(any());
    }
}
