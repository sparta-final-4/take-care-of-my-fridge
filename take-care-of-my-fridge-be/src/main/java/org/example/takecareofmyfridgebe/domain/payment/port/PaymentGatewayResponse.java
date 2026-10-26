package org.example.takecareofmyfridgebe.domain.payment.port;

import java.time.LocalDateTime;

public record PaymentGatewayResponse(
        String id,
        String status,
        Long totalAmount,   // PG 응답에 금액이 없으면 null
        long cancelledAmount, // 총 취소 금액. TODO: 환불 도메인에서 사용
        LocalDateTime paidAt // PG가 기록한 결제 완료 시각(서버 시간대). 결제 완료 전이면 null
) {
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    public boolean isPaid() {
        return STATUS_PAID.equals(status);
    }

    // 결제가 더 진행될 수 없는 상태
    public boolean isFailed() {
        return STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    // 전액 취소된 상태. 부분 취소(PARTIAL_CANCELLED)는 포함하지 않는다
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }
}
