package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentPinResetLimiterTest {

    private static final Long MEMBER_ID = 7L;
    private static final String KEY = "payment:pin-reset-attempt:7";
    private static final String WINDOW_MILLIS = "1800000";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private PaymentPinResetLimiter paymentPinResetLimiter;

    private void givenCount(Long count) {
        given(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS)))
                .willReturn(count);
    }

    @Test
    @DisplayName("5번째 시도까지는 받고, 횟수 증가와 30분 만료를 한 번에 요청한다")
    void acquire_allow_whenWithinLimit() {
        givenCount(5L);

        assertThatCode(() -> paymentPinResetLimiter.acquire(MEMBER_ID)).doesNotThrowAnyException();

        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS));
    }

    @Test
    @DisplayName("30분 안에 6번째로 시도하면 재설정을 막는다")
    void acquire_throwException_whenOverLimit() {
        givenCount(6L);

        assertThatThrownBy(() -> paymentPinResetLimiter.acquire(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_RESET_LIMITED);
    }

    @Test
    @DisplayName("Redis 장애면 횟수를 셀 수 없어 재설정을 막는다")
    void acquire_throwUnavailable_whenRedisDown() {
        given(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of(KEY)), eq(WINDOW_MILLIS)))
                .willThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> paymentPinResetLimiter.acquire(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_RESET_UNAVAILABLE);
    }

    @Test
    @DisplayName("시도 횟수를 지우고, Redis 장애여도 예외를 던지지 않는다")
    void clear_deleteKey() {
        paymentPinResetLimiter.clear(MEMBER_ID);
        verify(stringRedisTemplate).delete(KEY);

        given(stringRedisTemplate.delete(KEY)).willThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> paymentPinResetLimiter.clear(MEMBER_ID)).doesNotThrowAnyException();
    }
}
