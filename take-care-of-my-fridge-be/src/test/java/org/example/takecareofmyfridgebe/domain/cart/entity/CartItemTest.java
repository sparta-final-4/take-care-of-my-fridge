package org.example.takecareofmyfridgebe.domain.cart.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemTest {

    @Test
    @DisplayName("빌더로 만들면 장바구니·상품·수량이 그대로 담긴다")
    void builderLinksCartAndKeepsProductAndQuantity() {
        Cart cart = Cart.builder().memberId(1L).build();

        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(2).build();

        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getProductId()).isEqualTo(10L);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("수량 변경은 수량을 새 값으로 바꾼다")
    void changeQuantityReplacesQuantity() {
        CartItem item = CartItem.builder().cart(Cart.builder().memberId(1L).build()).productId(10L).quantity(2).build();

        item.changeQuantity(5);

        assertThat(item.getQuantity()).isEqualTo(5);
    }
}
