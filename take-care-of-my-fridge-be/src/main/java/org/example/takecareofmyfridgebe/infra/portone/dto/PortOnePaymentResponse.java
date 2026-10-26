package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// PortOne V2 결제 단건 조회 응답 중 검증과 환불 대조에 쓰는 필드만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String status,
        Amount amount,
        Instant paidAt,
        List<Cancellation> cancellations // 취소·부분 취소된 결제에만 있다
) {
    private static final String CANCELLATION_SUCCEEDED = "SUCCEEDED";

    public PaymentGatewayResponse toGatewayResponse() {
        return new PaymentGatewayResponse(
                id,
                status,
                amount == null ? null : amount.total(),
                amount == null || amount.cancelled() == null ? 0L : amount.cancelled(),
                // 만료 시각(expires_at)이 서버 시간대의 LocalDateTime으로 저장되므로 같은 기준으로 바꿔 비교한다
                paidAt == null ? null : LocalDateTime.ofInstant(paidAt, ZoneId.systemDefault()),
                cancellations == null ? List.of() : cancellations.stream()
                        .map(cancellation -> new PaymentGatewayResponse.Cancellation(
                                cancellation.id(),
                                cancellation.totalAmount() == null ? 0L : cancellation.totalAmount(),
                                CANCELLATION_SUCCEEDED.equals(cancellation.status())))
                        .toList()
        );
    }

    // Jackson 3는 원시 타입 필드가 응답에 없으면 파싱을 실패시키므로, 없을 수 있는 값은 래퍼 타입으로 받는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            long total,
            Long cancelled
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
            String status,
            String id,
            Long totalAmount
    ) {
    }
}
