package org.example.takecareofmyfridgebe.domain.order.entity;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Order newOrder() {
        return Order.builder().orderNumber("20261013-000001").memberId(1L).totalAmount(10000L).build();
    }

    @Test
    void newOrderStartsPending() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    void markPaidRecordsPaidAt() {
        Order order = newOrder();
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 12, 0);

        order.markPaid(now);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(now);
    }

    @Test
    void cancelWorksOnlyWhilePending() {
        Order order = newOrder();
        order.markPaid(LocalDateTime.now());

        assertThatThrownBy(order::cancel).isInstanceOf(BusinessException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void confirmWorksOnlyAfterPaid() {
        Order order = newOrder();

        assertThatThrownBy(order::confirm).isInstanceOf(BusinessException.class);

        order.markPaid(LocalDateTime.now());
        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
