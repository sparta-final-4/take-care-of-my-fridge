package org.example.takecareofmyfridgebe.domain.refund.dto;

import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;

// 요청 상태로 저장한 환불. PG 취소에 필요한 값을 담는다
public record RefundRequested(
        Long refundId,
        String portonePaymentId,
        Long refundAmount,
        String reason
) {
    public static RefundRequested from(Refund refund) {
        return new RefundRequested(refund.getId(), refund.getPayment().getPortonePaymentId(),
                refund.getRefundAmount(), refund.getReasonType().getDescription());
    }
}
