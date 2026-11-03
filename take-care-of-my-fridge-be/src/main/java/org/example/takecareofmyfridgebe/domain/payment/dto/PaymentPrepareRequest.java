package org.example.takecareofmyfridgebe.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;

public record PaymentPrepareRequest(
        @NotNull(message = "결제대행사는 필수입니다.")
        PgProvider pgProvider
) {
}
