package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;

// PortOne V2 결제 단건 조회 응답 중 검증에 쓰는 필드만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String status,
        Amount amount
) {
    public PaymentGatewayResponse toGatewayResponse() {
        return new PaymentGatewayResponse(
                id,
                status,
                amount == null ? null : amount.total(),
                amount == null ? 0L : amount.cancelled()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            long total,
            long cancelled
    ) {
    }
}
