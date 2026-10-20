package org.example.takecareofmyfridgebe.domain.payment.port;

public record PaymentGatewayResponse(
        String id,
        String status,
        Long totalAmount,   // PG 응답에 금액이 없으면 null
        long cancelledAmount // 총 취소 금액. TODO: 환불 도메인에서 사용
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
}
