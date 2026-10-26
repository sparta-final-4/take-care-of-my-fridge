package org.example.takecareofmyfridgebe.domain.refund.dto;

// 주문 목록·주문 상세에 보여 줄 주문 하나의 환불 요약. refundedAmount는 완료된 환불 금액의 합
public record RefundSummaryResponse(
        Long orderId,
        RefundSummaryStatus refundStatus,
        long refundedAmount
) {
}
