package org.example.takecareofmyfridgebe.domain.order.service;

import static org.mockito.ArgumentMatchers.any;
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
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class OrderAutoConfirmServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderConfirmService confirmService = mock(OrderConfirmService.class);
    private final OrderAutoConfirmService service = new OrderAutoConfirmService(orderRepository, confirmService);
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 10, 22, 10, 0);

    @Test
    void skipsRequestedRefundAndContinuesWithNextOrder() {
        Order first = order(1L, 10L);
        Order second = order(2L, 20L);
        when(orderRepository.findPaidBefore(eq(OrderStatus.PAID), eq(cutoff), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        doThrow(new BusinessException(ErrorCode.ORDER_REFUND_IN_PROGRESS))
                .when(confirmService).confirm(10L, 1L);

        service.confirmDue(cutoff);

        verify(confirmService).confirm(10L, 1L);
        verify(confirmService).confirm(20L, 2L);
    }

    @Test
    void readsNextBatchAfterLastProcessedId() {
        List<Order> firstBatch = LongStream.rangeClosed(1, OrderAutoConfirmService.BATCH_SIZE)
                .mapToObj(id -> order(id, 10L)).toList();
        Order next = order(500L, 20L);
        when(orderRepository.findPaidBefore(eq(OrderStatus.PAID), eq(cutoff), anyLong(), any(Pageable.class)))
                .thenReturn(firstBatch, List.of(next));

        service.confirmDue(cutoff);

        verify(orderRepository).findPaidBefore(eq(OrderStatus.PAID), eq(cutoff),
                eq((long) OrderAutoConfirmService.BATCH_SIZE), any(Pageable.class));
        verify(confirmService).confirm(20L, 500L);
    }

    private Order order(Long id, Long memberId) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }
}
