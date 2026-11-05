package org.example.takecareofmyfridgebe.domain.payment.entity;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentPinTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    private final PaymentPin pin = PaymentPin.builder().memberId(1L).pinHash("hash-1").build();

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            pin.recordFailure(NOW);
        }
    }

    @Test
    @DisplayName("처음 설정하면 틀린 횟수 0, 잠기지 않음, 남은 기회 5번이다")
    void build_createUnlockedPin() {
        assertThat(pin.getFailedCount()).isZero();
        assertThat(pin.isLocked()).isFalse();
        assertThat(pin.remainingAttempts()).isEqualTo(PaymentPin.MAX_FAILED_COUNT);
    }

    @Test
    @DisplayName("4번 틀리면 잠기지 않고 남은 기회가 1번이다")
    void recordFailure_keepUnlocked_whenFailedFourTimes() {
        failTimes(4);

        assertThat(pin.isLocked()).isFalse();
        assertThat(pin.remainingAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("5번 연속 틀리면 그 시각으로 잠긴다")
    void recordFailure_lock_whenFailedFiveTimes() {
        failTimes(5);

        assertThat(pin.isLocked()).isTrue();
        assertThat(pin.getLockedAt()).isEqualTo(NOW);
        assertThat(pin.remainingAttempts()).isZero();
    }

    @Test
    @DisplayName("맞히면 틀린 횟수가 0으로 돌아가 연속으로 센다")
    void recordMatched_resetFailedCount() {
        failTimes(3);

        pin.recordMatched();

        assertThat(pin.getFailedCount()).isZero();
        assertThat(pin.remainingAttempts()).isEqualTo(PaymentPin.MAX_FAILED_COUNT);
    }

    @Test
    @DisplayName("잠긴 뒤에는 맞혀도·틀려도·바꾸려 해도 잠김 오류가 난다")
    void recordAndChange_throwException_whenLocked() {
        failTimes(5);

        for (Runnable action : new Runnable[]{pin::recordMatched, () -> pin.recordFailure(NOW),
                () -> pin.change("hash-2")}) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_LOCKED);
        }
    }

    @Test
    @DisplayName("바꾸면 새 해시가 되고 틀린 횟수가 0이 된다")
    void change_replaceHashAndResetFailedCount() {
        failTimes(2);

        pin.change("hash-2");

        assertThat(pin.getPinHash()).isEqualTo("hash-2");
        assertThat(pin.getFailedCount()).isZero();
    }

    @Test
    @DisplayName("재설정하면 잠김이 풀리고 새 해시와 틀린 횟수 0이 된다")
    void reset_unlockAndReplaceHash_whenLocked() {
        failTimes(5);

        pin.reset("hash-3");

        assertThat(pin.isLocked()).isFalse();
        assertThat(pin.getLockedAt()).isNull();
        assertThat(pin.getPinHash()).isEqualTo("hash-3");
        assertThat(pin.getFailedCount()).isZero();
    }

    @Test
    @DisplayName("같은 숫자만 쓰거나 1씩 오르내리는 숫자는 쉬운 비밀번호로 거절하고 그 밖은 받는다")
    void validateStrength_throwException_whenWeak() {
        for (String weak : List.of("000000", "123456", "345678", "654321", "987654")) {
            assertThatThrownBy(() -> PaymentPin.validateStrength(weak))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_WEAK);
        }
        for (String ok : List.of("271828", "112233", "098765", "135790")) {
            assertThatCode(() -> PaymentPin.validateStrength(ok)).doesNotThrowAnyException();
        }
    }
}
