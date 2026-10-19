package org.example.takecareofmyfridgebe.domain.cart.dto;

import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;

public record AddCartItemResponse(Long productId, int quantity) {

    public static AddCartItemResponse from(CartItem item) {
        return new AddCartItemResponse(item.getProductId(), item.getQuantity());
    }
}
