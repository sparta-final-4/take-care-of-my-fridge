package org.example.takecareofmyfridgebe.domain.refund.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// PG 누적 취소 금액과 맞춰 볼 결제 하나의 환불 기록.
// knownCancelledAmount는 완료된 환불과 환불이 아닌 PG 취소(콘솔 취소 등)로 이미 기록한 금액의 합이다
public record RefundSyncState(
        Long paymentId,
        boolean paid,
        long knownCancelledAmount,
        List<RequestedRefund> requestedRefunds, // 오래된 순
        Set<String> recordedCancellationIds
) {
    public record RequestedRefund(
            Long refundId,
            long amount,
            LocalDateTime requestedAt
    ) {
    }
}
