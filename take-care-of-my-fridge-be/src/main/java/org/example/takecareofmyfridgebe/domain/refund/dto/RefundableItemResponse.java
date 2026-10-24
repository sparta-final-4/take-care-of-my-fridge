package org.example.takecareofmyfridgebe.domain.refund.dto;

import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;

// 환불 신청 화면의 주문 상품 한 줄. 특가·번개 드롭 상품은 refundable=false, refundableQuantity=0
public record RefundableItemResponse(
        Long orderItemId,
        Long productId,
        String productName,
        String productImageUrl,
        int unitPrice,
        int orderedQuantity,
        int refundedQuantity,
        int refundableQuantity,
        SaleType saleType,
        boolean refundable
) {
    public static RefundableItemResponse of(OrderItem item, int refundedQuantity, boolean refundableItem) {
        int refundableQuantity = refundableItem ? item.getQuantity() - refundedQuantity : 0;
        return new RefundableItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductImageUrl(),
                item.getUnitPrice(),
                item.getQuantity(),
                refundedQuantity,
                refundableQuantity,
                item.getSaleType(),
                refundableQuantity > 0
        );
    }
}
