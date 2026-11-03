package org.example.takecareofmyfridgebe.domain.order.entity;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Order newOrder() {
        return Order.builder().orderNumber("20261013-000001").memberId(1L).totalAmount(10000L).build();
    }

    @Test
    @DisplayName("새 주문은 결제 대기 상태로 시작한다")
    void newOrderStartsPending() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("결제 완료 처리하면 결제 시각이 기록된다")
    void markPaidRecordsPaidAt() {
        Order order = newOrder();
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 12, 0);

        order.markPaid(now);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("결제 대기 상태에서만 취소할 수 있다")
    void cancelWorksOnlyWhilePending() {
        Order order = newOrder();
        order.markPaid(LocalDateTime.now());

        assertThatThrownBy(order::cancel).isInstanceOf(BusinessException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 완료 주문을 취소하면 이미 결제된 주문 오류가 난다")
    void cancelRejectsPaidOrderAsAlreadyPaid() {
        Order order = newOrder();
        order.markPaid(LocalDateTime.now());

        assertThatThrownBy(order::cancel)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
    }

    @Test
    @DisplayName("이미 취소된 주문을 취소하면 취소된 주문 오류가 난다")
    void cancelRejectsCancelledOrder() {
        Order order = newOrder();
        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(order::cancel)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_CANCELLED);
    }

    @Test
    @DisplayName("결제 완료 뒤에만 구매 확정할 수 있다")
    void confirmWorksOnlyAfterPaid() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.confirm(LocalDateTime.now())).isInstanceOf(BusinessException.class);

        order.markPaid(LocalDateTime.now());
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 6, 0);
        order.confirm(now);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("결제 대기 주문을 구매 확정하면 결제되지 않은 주문 오류가 난다")
    void confirmRejectsPendingOrderAsNotPaid() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.confirm(LocalDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("취소된 주문을 구매 확정하면 취소된 주문 오류가 난다")
    void confirmRejectsCancelledOrderAsCancelled() {
        Order order = newOrder();
        order.cancel();

        assertThatThrownBy(() -> order.confirm(LocalDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("구매 확정 뒤에만 구매 확정 여부가 참이다")
    void isConfirmedIsTrueOnlyAfterConfirm() {
        Order order = newOrder();
        assertThat(order.isConfirmed()).isFalse();

        order.markPaid(LocalDateTime.of(2026, 10, 21, 12, 0));
        assertThat(order.isConfirmed()).isFalse();

        order.confirm(LocalDateTime.of(2026, 10, 22, 12, 0));
        assertThat(order.isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("결제 대기 주문은 결제 대기 검증을 통과한다")
    void validatePendingPassesForPendingOrder() {
        newOrder().validatePending();
    }

    @Test
    @DisplayName("결제 완료·구매 확정 주문은 결제 대기 검증에서 이미 결제된 주문 오류가 난다")
    void validatePendingRejectsPaidAndConfirmedOrder() {
        Order paid = newOrder();
        paid.markPaid(LocalDateTime.of(2026, 10, 21, 12, 0));
        Order confirmed = newOrder();
        confirmed.markPaid(LocalDateTime.of(2026, 10, 21, 12, 0));
        confirmed.confirm(LocalDateTime.of(2026, 10, 28, 12, 0));

        assertThatThrownBy(paid::validatePending)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
        assertThatThrownBy(confirmed::validatePending)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
    }

    @Test
    @DisplayName("취소된 주문은 결제 대기 검증에서 취소된 주문 오류가 난다")
    void validatePendingRejectsCancelledOrder() {
        Order cancelled = newOrder();
        cancelled.cancel();

        assertThatThrownBy(cancelled::validatePending)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_CANCELLED);
    }

    @Test
    @DisplayName("결제 완료 주문을 전액 환불 상태로 바꾼다")
    void markRefundedChangesPaidOrderToRefunded() {
        Order order = newOrder();
        order.markPaid(LocalDateTime.of(2026, 11, 2, 12, 0));

        order.markRefunded();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("결제 대기·구매 확정 주문은 전액 환불 상태로 바꿀 수 없다")
    void markRefundedRejectsPendingAndConfirmedOrder() {
        Order pending = newOrder();
        Order confirmed = newOrder();
        confirmed.markPaid(LocalDateTime.of(2026, 11, 2, 12, 0));
        confirmed.confirm(LocalDateTime.of(2026, 11, 9, 12, 0));

        assertThatThrownBy(pending::markRefunded)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThatThrownBy(confirmed::markRefunded)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("전액 환불된 주문을 구매 확정하면 전액 환불 주문 오류가 난다")
    void confirmRejectsRefundedOrderAsFullyRefunded() {
        Order order = newOrder();
        order.markPaid(LocalDateTime.of(2026, 11, 2, 12, 0));
        order.markRefunded();

        assertThatThrownBy(() -> order.confirm(LocalDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_FULLY_REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getConfirmedAt()).isNull();
    }
}
