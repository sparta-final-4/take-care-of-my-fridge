package org.example.takecareofmyfridgebe.domain.refund.entity;

import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundItemTest {

    private final OrderItem orderItem = OrderItem.builder().quantity(3).listPrice(12000).unitPrice(9000).build();

    @Test
    @DisplayName("주문 당시 단가를 복사하고 금액은 단가 × 수량이다")
    void copiesUnitPriceAndCalculatesAmount() {
        RefundItem refundItem = RefundItem.builder().orderItem(orderItem).quantity(2).build();

        assertThat(refundItem.getUnitPrice()).isEqualTo(9000);
        assertThat(refundItem.getAmount()).isEqualTo(18000L);
    }

    @Test
    @DisplayName("단가 × 수량이 int 범위를 넘어도 금액이 넘치지 않는다")
    void amount_doesNotOverflow() {
        OrderItem expensive = OrderItem.builder().quantity(3).unitPrice(Integer.MAX_VALUE).build();

        RefundItem refundItem = RefundItem.builder().orderItem(expensive).quantity(2).build();

        assertThat(refundItem.getAmount()).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("환불 수량이 1보다 작으면 만들 수 없다")
    void quantityLessThanOne_isRejected(int quantity) {
        assertThatThrownBy(() -> RefundItem.builder().orderItem(orderItem).quantity(quantity).build())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFUND_QUANTITY);
    }
}
