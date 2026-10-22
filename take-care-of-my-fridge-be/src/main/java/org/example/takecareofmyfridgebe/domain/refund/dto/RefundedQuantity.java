package org.example.takecareofmyfridgebe.domain.refund.dto;

// 주문 상품별 누적 환불 수량
public record RefundedQuantity(Long orderItemId, long quantity) {
}
