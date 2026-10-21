package org.example.takecareofmyfridgebe.domain.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 20, 10, 10);

    private final Payment payment = Payment.builder().portonePaymentId("payment-1").amount(10000L)
            .expiresAt(EXPIRES_AT).build();

    @Test
    @DisplayName("만료 시각 이전이나 같은 시각에 결제가 끝났으면 기한 내다")
    void paidOnOrBeforeExpiry_isInTime() {
        assertThat(payment.isPaidAfterExpiry(EXPIRES_AT.minusSeconds(1))).isFalse();
        assertThat(payment.isPaidAfterExpiry(EXPIRES_AT)).isFalse();
    }

    @Test
    @DisplayName("만료 시각보다 늦게 결제가 끝났으면 만료 후 결제다")
    void paidAfterExpiry_isExpired() {
        assertThat(payment.isPaidAfterExpiry(EXPIRES_AT.plusNanos(1))).isTrue();
    }
}
