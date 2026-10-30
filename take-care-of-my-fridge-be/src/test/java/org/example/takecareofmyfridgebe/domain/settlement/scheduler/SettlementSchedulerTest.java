package org.example.takecareofmyfridgebe.domain.settlement.scheduler;

import org.example.takecareofmyfridgebe.domain.settlement.facade.SettlementFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 29);

    @Mock
    private SettlementFacade settlementFacade;

    @InjectMocks
    private SettlementScheduler settlementScheduler;

    @Test
    @DisplayName("어제 날짜로 집계한다")
    void aggregateYesterday_aggregatesYesterday() {
        settlementScheduler.aggregateYesterday(TODAY);

        verify(settlementFacade).aggregate(YESTERDAY);
    }

    @Test
    @DisplayName("월초에는 지난달 말일을 집계한다")
    void aggregateYesterday_firstDayOfMonth_aggregatesLastDayOfPreviousMonth() {
        settlementScheduler.aggregateYesterday(LocalDate.of(2026, 11, 1));

        verify(settlementFacade).aggregate(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("집계에 실패해도 예외를 밖으로 던지지 않는다")
    void aggregateYesterday_failure_doesNotThrow() {
        willThrow(new IllegalStateException("DB 오류")).given(settlementFacade).aggregate(YESTERDAY);

        assertThatCode(() -> settlementScheduler.aggregateYesterday(TODAY)).doesNotThrowAnyException();
    }
}
