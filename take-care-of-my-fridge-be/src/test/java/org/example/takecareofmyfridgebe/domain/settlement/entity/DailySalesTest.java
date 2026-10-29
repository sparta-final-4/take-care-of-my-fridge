package org.example.takecareofmyfridgebe.domain.settlement.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailySalesTest {

    private static final LocalDate SALES_DATE = LocalDate.of(2026, 10, 7);

    private final DailySales dailySales = DailySales.builder()
            .salesDate(SALES_DATE)
            .paidAmount(30000L)
            .refundedAmount(5000L)
            .paidCount(3)
            .build();

    @Test
    @DisplayName("순매출은 결제 금액에서 환불 금액을 뺀 값이다")
    void netAmount_isPaidMinusRefunded() {
        assertThat(dailySales.getSalesDate()).isEqualTo(SALES_DATE);
        assertThat(dailySales.getNetAmount()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("이미 집계한 날을 다시 집계하면 새로 계산한 합계로 바꾼다")
    void reaggregate_replacesValues() {
        dailySales.reaggregate(40000L, 10000L, 4);

        assertThat(dailySales.getPaidAmount()).isEqualTo(40000L);
        assertThat(dailySales.getRefundedAmount()).isEqualTo(10000L);
        assertThat(dailySales.getPaidCount()).isEqualTo(4);
        assertThat(dailySales.getNetAmount()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("환불이 그날 결제보다 많으면 순매출은 음수다")
    void netAmount_canBeNegative() {
        dailySales.reaggregate(10000L, 30000L, 1);

        assertThat(dailySales.getNetAmount()).isEqualTo(-20000L);
    }
}
