package org.example.takecareofmyfridgebe.domain.cart.dto;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;

public record CartItemsResponse(List<CartItemResponse> items) {

    public static CartItemsResponse from(List<CartItem> items) {
        return new CartItemsResponse(items.stream().map(CartItemResponse::from).toList());
    }
}
