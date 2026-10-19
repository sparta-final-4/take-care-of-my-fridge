package org.example.takecareofmyfridgebe.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// amount가 null이면 PortOne이 남은 금액 전액을 취소한다
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortOneCancelRequest(
        Long amount,
        String reason,
        String storeId
) {
}
