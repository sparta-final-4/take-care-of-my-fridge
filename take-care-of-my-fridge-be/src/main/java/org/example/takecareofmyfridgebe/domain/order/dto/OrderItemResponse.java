package org.example.takecareofmyfridgebe.domain.order.dto;

import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;

public record OrderItemResponse(
        Long productId,
        String productName,
        String productImageUrl,
        int quantity,
        int listPrice,
        int unitPrice,
        long lineAmount,
        SaleType saleType
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getProductImageUrl(),
                item.getQuantity(),
                item.getListPrice(),
                item.getUnitPrice(),
                (long) item.getUnitPrice() * item.getQuantity(),
                item.getSaleType()
        );
    }
}
