package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// PortOne V2 결제 취소 응답 중 취소 건 ID만 받는다
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneCancelResponse(
        Cancellation cancellation
) {
    public String cancellationId() {
        return cancellation == null ? null : cancellation.id();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
            String id
    ) {
    }
}
