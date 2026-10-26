package org.example.takecareofmyfridgebe.domain.refund.dto;

import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;

import java.time.LocalDateTime;

// 환불 완료 전에는 refundedAt이 null
public record RefundResponse(
        Long refundId,
        RefundStatus status,
        Long refundAmount,
        LocalDateTime refundedAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getStatus(), refund.getRefundAmount(),
                refund.getRefundedAt());
    }

    // PG 취소 결과를 확정하지 못해 요청 상태로 남은 환불. 웹훅·스케줄러가 마무리한다
    public static RefundResponse requested(RefundRequested refund) {
        return new RefundResponse(refund.refundId(), RefundStatus.REQUESTED, refund.refundAmount(), null);
    }
}
