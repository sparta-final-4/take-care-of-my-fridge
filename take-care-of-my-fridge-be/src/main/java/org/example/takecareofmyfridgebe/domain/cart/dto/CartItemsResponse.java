package org.example.takecareofmyfridgebe.domain.cart.dto;

import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;

public record CartItemsResponse(List<CartItemResponse> items) {

    public static CartItemsResponse from(List<CartItem> items, Map<Long, Product> productsById,
                                         Map<Long, ActiveSale> salesByProductId) {
        return new CartItemsResponse(items.stream()
                .map(item -> CartItemResponse.from(item, productsById.get(item.getProductId()), salesByProductId.get(item.getProductId())))
                .toList());
    }
}
