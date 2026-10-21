package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// PortOne V2 결제 단건 조회 응답 중 검증에 쓰는 필드만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String status,
        Amount amount,
        Instant paidAt
) {
    public PaymentGatewayResponse toGatewayResponse() {
        return new PaymentGatewayResponse(
                id,
                status,
                amount == null ? null : amount.total(),
                amount == null || amount.cancelled() == null ? 0L : amount.cancelled(),
                // 만료 시각(expires_at)이 서버 시간대의 LocalDateTime으로 저장되므로 같은 기준으로 바꿔 비교한다
                paidAt == null ? null : LocalDateTime.ofInstant(paidAt, ZoneId.systemDefault())
        );
    }

    // Jackson 3는 원시 타입 필드가 응답에 없으면 파싱을 실패시키므로, 없을 수 있는 값은 래퍼 타입으로 받는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            long total,
            Long cancelled
    ) {
    }
}
