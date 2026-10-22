package org.example.takecareofmyfridgebe.domain.refund.entity;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundTest {

    private static final LocalDateTime REFUNDED_AT = LocalDateTime.of(2026, 10, 22, 10, 0);

    private final Refund refund = Refund.builder().refundAmount(10000L)
            .reasonType(RefundReasonType.CHANGE_OF_MIND).build();

    @Test
    @DisplayName("새 환불은 요청 상태다")
    void newRefund_isRequested() {
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("요청 상태의 환불을 완료하면 취소 ID와 환불 시각이 남는다")
    void complete_requested() {
        refund.complete("cancellation-1", REFUNDED_AT);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancellation-1");
        assertThat(refund.getRefundedAt()).isEqualTo(REFUNDED_AT);
    }

    @Test
    @DisplayName("요청 상태의 환불을 실패 처리하면 사유가 남고 환불 시각은 비어 있다")
    void fail_requested() {
        refund.fail("PG 오류");

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailReason()).isEqualTo("PG 오류");
        assertThat(refund.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("실패 사유가 컬럼 길이를 넘으면 잘라서 저장한다")
    void fail_truncatesLongReason() {
        refund.fail("가".repeat(300));

        assertThat(refund.getFailReason()).hasSize(255);
    }

    @Test
    @DisplayName("완료된 환불은 다시 완료하거나 실패 처리할 수 없다")
    void completed_cannotChange() {
        refund.complete("cancellation-1", REFUNDED_AT);

        assertInvalidState(() -> refund.complete("cancellation-2", REFUNDED_AT));
        assertInvalidState(() -> refund.fail("PG 오류"));
    }

    @Test
    @DisplayName("실패한 환불은 다시 완료하거나 실패 처리할 수 없다")
    void failed_cannotChange() {
        refund.fail("PG 오류");

        assertInvalidState(() -> refund.complete("cancellation-1", REFUNDED_AT));
        assertInvalidState(() -> refund.fail("PG 오류"));
    }

    private void assertInvalidState(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFUND_STATE);
    }
}
