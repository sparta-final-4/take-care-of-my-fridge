package org.example.takecareofmyfridgebe.domain.order.scheduler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.facade.OrderStatusFacade;
import org.example.takecareofmyfridgebe.domain.order.service.OrderStatusService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 어떤 주문을 확정하는지는 OrderStatusFacadeConfirmTransactionTest가 실제 DB로 확인한다. 여기서는 기준 시각, 건너뛰기, 나눠 읽기만 본다.
class OrderAutoConfirmSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0);
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 22, 10, 0);

    private final OrderStatusService orderStatusService = mock(OrderStatusService.class);
    private final OrderStatusFacade orderStatusFacade = mock(OrderStatusFacade.class);
    private final OrderAutoConfirmScheduler scheduler = new OrderAutoConfirmScheduler(orderStatusService, orderStatusFacade);

    @Test
    @DisplayName("결제 7일 뒤를 기준 시각으로 확정 대상을 조회한다")
    void usesExactlySevenDaysAfterPaymentAsCutoff() {
        scheduler.confirmDueOrders(NOW);

        verify(orderStatusService).findPaidOrders(CUTOFF, 0L, OrderAutoConfirmScheduler.BATCH_SIZE);
    }

    @Test
    @DisplayName("환불이 진행 중인 주문은 건너뛰고 다음 주문을 확정한다")
    void skipsRequestedRefundAndContinuesWithNextOrder() {
        List<Order> orders = List.of(order(1L, 10L), order(2L, 20L));
        when(orderStatusService.findPaidOrders(CUTOFF, 0L, OrderAutoConfirmScheduler.BATCH_SIZE)).thenReturn(orders);
        doThrow(new BusinessException(ErrorCode.ORDER_REFUND_IN_PROGRESS))
                .when(orderStatusFacade).confirm(10L, 1L);

        scheduler.confirmDueOrders(NOW);

        verify(orderStatusFacade).confirm(10L, 1L);
        verify(orderStatusFacade).confirm(20L, 2L);
    }

    @Test
    @DisplayName("전액 환불된 주문은 건너뛰고 다음 주문을 확정한다")
    void skipsFullyRefundedAndContinuesWithNextOrder() {
        List<Order> orders = List.of(order(1L, 10L), order(2L, 20L));
        when(orderStatusService.findPaidOrders(CUTOFF, 0L, OrderAutoConfirmScheduler.BATCH_SIZE)).thenReturn(orders);
        doThrow(new BusinessException(ErrorCode.ORDER_FULLY_REFUNDED))
                .when(orderStatusFacade).confirm(10L, 1L);

        scheduler.confirmDueOrders(NOW);

        verify(orderStatusFacade).confirm(10L, 1L);
        verify(orderStatusFacade).confirm(20L, 2L);
    }

    @Test
    @DisplayName("마지막으로 처리한 주문 id 뒤부터 다음 묶음을 읽는다")
    void readsNextBatchAfterLastProcessedId() {
        List<Order> firstBatch = LongStream.rangeClosed(1, OrderAutoConfirmScheduler.BATCH_SIZE)
                .mapToObj(id -> order(id, 10L)).toList();
        Order next = order(500L, 20L);
        when(orderStatusService.findPaidOrders(eq(CUTOFF), anyLong(), eq(OrderAutoConfirmScheduler.BATCH_SIZE)))
                .thenReturn(firstBatch, List.of(next));

        scheduler.confirmDueOrders(NOW);

        verify(orderStatusService).findPaidOrders(CUTOFF, OrderAutoConfirmScheduler.BATCH_SIZE,
                OrderAutoConfirmScheduler.BATCH_SIZE);
        verify(orderStatusFacade).confirm(20L, 500L);
    }

    private Order order(Long id, Long memberId) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }
}
