package org.example.takecareofmyfridgebe.domain.cart.dto;

import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;

public record ChangeCartItemQuantityResponse(Long cartItemId, int quantity) {

    public static ChangeCartItemQuantityResponse from(CartItem item) {
        return new ChangeCartItemQuantityResponse(item.getId(), item.getQuantity());
    }
}
