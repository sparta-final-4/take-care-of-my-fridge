package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// PortOne V2 결제 단건 조회 응답 중 검증에 쓰는 필드만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String status,
        Amount amount
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            long total
    ) {
    }
}
