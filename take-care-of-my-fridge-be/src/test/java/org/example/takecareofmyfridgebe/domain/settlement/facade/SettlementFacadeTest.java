package org.example.takecareofmyfridgebe.domain.settlement.facade;

import org.example.takecareofmyfridgebe.domain.settlement.service.SettlementCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementFacadeTest {

    private static final LocalDate SALES_DATE = LocalDate.of(2026, 10, 28);

    @Mock
    private SettlementCommandService settlementCommandService;

    @InjectMocks
    private SettlementFacade settlementFacade;

    @Test
    @DisplayName("집계는 한 번만 실행한다")
    void aggregate_runsOnce() {
        settlementFacade.aggregate(SALES_DATE);

        verify(settlementCommandService, times(1)).aggregate(SALES_DATE);
    }

    @Test
    @DisplayName("같은 날 행을 동시에 만들어 UNIQUE 충돌이 나면 한 번 더 집계해 덮어쓴다")
    void aggregate_salesDateConflict_retriesOnce() {
        willThrow(conflict("Duplicate entry '2026-10-28' for key 'daily_sales.uk_daily_sales_sales_date'"))
                .willDoNothing()
                .given(settlementCommandService).aggregate(SALES_DATE);

        settlementFacade.aggregate(SALES_DATE);

        verify(settlementCommandService, times(2)).aggregate(SALES_DATE);
    }

    @Test
    @DisplayName("다른 제약 위반은 다시 시도하지 않고 그대로 던진다")
    void aggregate_otherViolation_rethrows() {
        DataIntegrityViolationException exception = conflict("Column 'sales_date' cannot be null");
        willThrow(exception).given(settlementCommandService).aggregate(SALES_DATE);

        assertThatThrownBy(() -> settlementFacade.aggregate(SALES_DATE)).isSameAs(exception);
        verify(settlementCommandService, times(1)).aggregate(SALES_DATE);
    }

    private DataIntegrityViolationException conflict(String message) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(message));
    }
}
