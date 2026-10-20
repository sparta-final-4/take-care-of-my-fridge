package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// PortOne V2 오류 응답. type으로 오류 종류를 구분한다 (예: PAYMENT_ALREADY_CANCELLED)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneErrorResponse(
        String type,
        String message
) {
}
