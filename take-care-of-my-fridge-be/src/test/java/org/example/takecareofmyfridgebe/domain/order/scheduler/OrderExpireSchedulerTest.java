package org.example.takecareofmyfridgebe.domain.order.scheduler;

import org.example.takecareofmyfridgebe.domain.order.service.OrderCancelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

// 어떤 주문을 지우는지는 OrderCancelServiceTransactionTest가 실제 DB로 확인한다. 여기서는 실패한 건과 나눠 읽기만 본다
@ExtendWith(MockitoExtension.class)
class OrderExpireSchedulerTest {

    @Mock
    private OrderCancelService orderCancelService;

    @InjectMocks
    private OrderExpireScheduler scheduler;

    @Test
    @DisplayName("한 건 처리가 실패해도 나머지 건은 계속 처리한다")
    void oneFailure_othersStillProcessed() {
        given(orderCancelService.findExpiredOrderIds(any(), eq(0L), eq(OrderExpireScheduler.BATCH_SIZE)))
                .willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("DB 오류")).given(orderCancelService).expire(2L);

        scheduler.expireAbandonedOrders();

        verify(orderCancelService).expire(1L);
        verify(orderCancelService).expire(3L);
    }

    @Test
    @DisplayName("한 번에 다 못 읽으면 마지막 id 다음부터 이어서 읽어 남은 건을 처리한다")
    void fullBatch_readsNextPageAfterLastId() {
        List<Long> fullPage = LongStream.rangeClosed(1, OrderExpireScheduler.BATCH_SIZE).boxed().toList();
        given(orderCancelService.findExpiredOrderIds(any(), anyLong(), eq(OrderExpireScheduler.BATCH_SIZE)))
                .willReturn(fullPage, List.of(500L));

        scheduler.expireAbandonedOrders();

        verify(orderCancelService).findExpiredOrderIds(any(), eq((long) OrderExpireScheduler.BATCH_SIZE),
                eq(OrderExpireScheduler.BATCH_SIZE));
        verify(orderCancelService).expire(500L);
    }
}
