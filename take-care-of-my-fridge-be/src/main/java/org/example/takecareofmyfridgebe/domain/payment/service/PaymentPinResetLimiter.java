package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

// 결제 비밀번호 재설정에서 로그인 비밀번호를 계속 넣어 보지 못하게 회원별 시도 횟수를 센다.
// 시도하기 전에 먼저 세므로 동시에 여러 번 보내도 한도를 넘지 못한다. 로그인 비밀번호가 맞으면 지운다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPinResetLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "payment:pin-reset-attempt:";

    // 횟수 증가와 만료 시간 설정을 한 번에 실행한다. 따로 부르면 증가 뒤 연결이 끊겼을 때 만료 없는 키가 남는다
    private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('incr', KEYS[1]) "
                    + "if redis.call('pttl', KEYS[1]) < 0 then redis.call('pexpire', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    // 첫 시도부터 30분 안에 5번을 넘으면 거절한다. 키가 만료되면 다시 시도할 수 있다.
    // Redis 장애면 횟수를 셀 수 없어 재설정을 막는다(막지 않으면 장애 동안 로그인 비밀번호를 무제한으로 맞혀 볼 수 있다)
    public void acquire(Long memberId) {
        Long count;
        try {
            count = stringRedisTemplate.execute(INCREMENT_SCRIPT, List.of(key(memberId)),
                    String.valueOf(WINDOW.toMillis()));
        } catch (DataAccessException e) {
            log.error("결제 비밀번호 재설정 시도 횟수 확인 실패, 재설정 거절: memberId={}", memberId, e);
            throw new BusinessException(ErrorCode.PAYMENT_PIN_RESET_UNAVAILABLE);
        }
        if (count == null) {
            throw new BusinessException(ErrorCode.PAYMENT_PIN_RESET_UNAVAILABLE);
        }
        if (count > MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.PAYMENT_PIN_RESET_LIMITED);
        }
    }

    // 지우지 못해도 30분 뒤 만료되므로 재설정을 막지 않는다
    public void clear(Long memberId) {
        try {
            stringRedisTemplate.delete(key(memberId));
        } catch (DataAccessException e) {
            log.warn("결제 비밀번호 재설정 시도 횟수 삭제 실패, 30분 뒤 만료됨: memberId={}", memberId, e);
        }
    }

    private static String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
