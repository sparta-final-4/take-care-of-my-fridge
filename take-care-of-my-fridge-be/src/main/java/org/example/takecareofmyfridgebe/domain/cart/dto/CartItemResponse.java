package org.example.takecareofmyfridgebe.domain.cart.dto;

import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;

public record CartItemResponse(Long cartItemId, Long productId, int quantity) {

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getId(), item.getProductId(), item.getQuantity());
    }
}
