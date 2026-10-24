package org.example.takecareofmyfridgebe.domain.refund.dto;

import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;

import java.time.LocalDateTime;
import java.util.List;

// 환불 상세 화면의 환불 한 건. PG 오류 원문(failReason)은 사용자에게 보여 주지 않는다. 완료 전에는 refundedAt이 null
public record RefundHistoryResponse(
        Long refundId,
        RefundStatus status,
        RefundReasonType reasonType,
        String reasonDetail,
        Long refundAmount,
        LocalDateTime requestedAt,
        LocalDateTime refundedAt,
        List<RefundHistoryItemResponse> items
) {
    public static RefundHistoryResponse from(Refund refund, List<RefundItem> items) {
        return new RefundHistoryResponse(
                refund.getId(),
                refund.getStatus(),
                refund.getReasonType(),
                refund.getReasonDetail(),
                refund.getRefundAmount(),
                refund.getCreatedAt(),
                refund.getRefundedAt(),
                items.stream().map(RefundHistoryItemResponse::from).toList()
        );
    }
}
