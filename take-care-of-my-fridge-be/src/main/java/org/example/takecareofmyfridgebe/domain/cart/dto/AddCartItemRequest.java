package org.example.takecareofmyfridgebe.domain.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;

public record AddCartItemRequest(
        @NotNull(message = "상품을 선택해 주세요.")
        Long productId,

        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity
) {

    // 수량을 생략하면 1개를 담는다
    public AddCartItemRequest {
        quantity = quantity == null ? 1 : quantity;
    }

    public CartItem toEntity(Cart cart) {
        return CartItem.builder()
                .cart(cart)
                .productId(productId)
                .quantity(quantity)
                .build();
    }
}
