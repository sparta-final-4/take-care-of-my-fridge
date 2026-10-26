package org.example.takecareofmyfridgebe.domain.sale.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaleEventSchedulerTest {

    @Mock
    private SaleEventStatusService saleEventStatusService;

    @InjectMocks
    private SaleEventScheduler saleEventScheduler;

    @Test
    @DisplayName("실행하면 지금 시각으로 상태를 정리한다")
    void openAndCloseDueEvents_callsStatusService() {
        saleEventScheduler.openAndCloseDueEvents();

        verify(saleEventStatusService).openAndCloseDue(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("상태 정리가 실패해도 예외를 밖으로 내지 않고 다음 실행에서 다시 처리한다")
    void openAndCloseDueEvents_swallowsFailure() {
        willThrow(new IllegalStateException("db down")).given(saleEventStatusService)
                .openAndCloseDue(any(LocalDateTime.class));

        assertThatCode(() -> saleEventScheduler.openAndCloseDueEvents()).doesNotThrowAnyException();
    }
}
