package org.example.takecareofmyfridgebe.domain.refund.dto;

import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;

// 단가·금액은 환불 신청 때 저장한 값을 쓴다
public record RefundHistoryItemResponse(
        Long orderItemId,
        String productName,
        String productImageUrl,
        int unitPrice,
        int quantity,
        Long amount
) {
    public static RefundHistoryItemResponse from(RefundItem item) {
        return new RefundHistoryItemResponse(
                item.getOrderItem().getId(),
                item.getOrderItem().getProductName(),
                item.getOrderItem().getProductImageUrl(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getAmount()
        );
    }
}
