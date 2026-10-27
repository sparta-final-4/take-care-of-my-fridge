package org.example.takecareofmyfridgebe.domain.order.dto;

import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;

// 주문 상세 화면의 주문 상품. 구매 확정된 주문만 확정 수량이 있고 확정 전에는 null이다.
public record OrderDetailItemResponse(
        Long productId,
        String productName,
        String productImageUrl,
        int quantity,
        Integer confirmedQuantity,
        int listPrice,
        int unitPrice,
        long lineAmount,
        SaleType saleType
) {

    public static OrderDetailItemResponse from(OrderItem item) {
        return new OrderDetailItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getProductImageUrl(),
                item.getQuantity(),
                item.getConfirmedQuantity(),
                item.getListPrice(),
                item.getUnitPrice(),
                (long) item.getUnitPrice() * item.getQuantity(),
                item.getSaleType()
        );
    }
}
