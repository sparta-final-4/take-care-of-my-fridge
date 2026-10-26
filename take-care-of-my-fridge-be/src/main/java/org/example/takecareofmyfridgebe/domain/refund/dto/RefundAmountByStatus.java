package org.example.takecareofmyfridgebe.domain.refund.dto;

import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;

// 주문별·환불 상태별 환불 금액 합
public record RefundAmountByStatus(Long orderId, RefundStatus status, long amount) {
}
