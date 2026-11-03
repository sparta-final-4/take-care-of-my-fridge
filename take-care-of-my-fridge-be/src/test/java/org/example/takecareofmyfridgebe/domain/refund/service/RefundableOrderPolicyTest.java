package org.example.takecareofmyfridgebe.domain.refund.service;

import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefundableOrderPolicyTest {

    @Test
    @DisplayName("일반 상품만 있는 주문의 모든 상품이 환불되면 전액 환불이다")
    void statusAfterRefund_allNormalRefunded_refunded() {
        List<OrderItem> items = List.of(item(1L, 2, SaleType.NONE), item(2L, 1, SaleType.NONE));

        assertThat(RefundableOrderPolicy.statusAfterRefund(items, Map.of(1L, 2L, 2L, 1L)))
                .contains(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("일반 상품이 모두 환불되고 특가·번개 드롭 상품이 남으면 구매 확정이다")
    void statusAfterRefund_onlySaleItemsLeft_confirmed() {
        List<OrderItem> items = List.of(item(1L, 2, SaleType.NONE), item(2L, 1, SaleType.FLASH_DROP),
                item(3L, 1, SaleType.SPECIAL));

        assertThat(RefundableOrderPolicy.statusAfterRefund(items, Map.of(1L, 2L)))
                .contains(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("환불되지 않은 일반 상품 수량이 남으면 주문 상태를 바꾸지 않는다")
    void statusAfterRefund_partial_empty() {
        List<OrderItem> items = List.of(item(1L, 2, SaleType.NONE), item(2L, 1, SaleType.NONE),
                item(3L, 1, SaleType.FLASH_DROP));

        assertThat(RefundableOrderPolicy.statusAfterRefund(items, Map.of(1L, 2L))).isEmpty();
        assertThat(RefundableOrderPolicy.statusAfterRefund(items, Map.of(1L, 1L, 2L, 1L))).isEmpty();
    }

    @Test
    @DisplayName("환불할 수 있는 상품이 없는 주문은 주문 상태를 바꾸지 않는다")
    void statusAfterRefund_noRefundableItem_empty() {
        assertThat(RefundableOrderPolicy.statusAfterRefund(List.of(), Map.of())).isEmpty();
        assertThat(RefundableOrderPolicy.statusAfterRefund(
                List.of(item(1L, 1, SaleType.SPECIAL), item(2L, 1, SaleType.FLASH_DROP)), Map.of())).isEmpty();
    }

    private OrderItem item(Long id, int quantity, SaleType saleType) {
        OrderItem item = OrderItem.builder().productId(id).productName("상품" + id)
                .quantity(quantity).listPrice(1000).unitPrice(1000).saleType(saleType).build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
