package org.example.takecareofmyfridgebe.domain.cart.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemTest {

    @Test
    void builderLinksCartAndKeepsProductAndQuantity() {
        Cart cart = Cart.builder().memberId(1L).build();

        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(2).build();

        assertThat(item.getCart()).isSameAs(cart);
        assertThat(item.getProductId()).isEqualTo(10L);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void changeQuantityReplacesQuantity() {
        CartItem item = CartItem.builder().cart(Cart.builder().memberId(1L).build()).productId(10L).quantity(2).build();

        item.changeQuantity(5);

        assertThat(item.getQuantity()).isEqualTo(5);
    }
}
