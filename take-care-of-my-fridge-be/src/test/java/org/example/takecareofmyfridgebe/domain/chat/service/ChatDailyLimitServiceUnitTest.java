package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
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
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatDailyLimitServiceUnitTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ChatProperties chatProperties;

    @InjectMocks
    private ChatDailyLimitService chatDailyLimitService;

    @Test
    @DisplayName("30번째 요청까지는 예외 없이 정상 통과한다")
    void checkAndIncrementPassesUnderLimit() {
        Long memberId = 1L;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(30L);
        when(stringRedisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(3600L);
        when(chatProperties.dailyLimit()).thenReturn(30);

        chatDailyLimitService.checkAndIncrement(memberId);

        verify(valueOperations, times(1)).increment(anyString());
        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("31번째 요청 시 429 CHAT_DAILY_LIMIT_EXCEEDED 예외가 발생한다")
    void checkAndIncrementThrows429WhenExceedsLimit() {
        Long memberId = 1L;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(31L);
        when(stringRedisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(3600L);
        when(chatProperties.dailyLimit()).thenReturn(30);

        assertThatThrownBy(() -> chatDailyLimitService.checkAndIncrement(memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);

        verify(valueOperations, times(1)).increment(anyString());
    }

    @Test
    @DisplayName("키 최초 생성 시 또는 TTL이 없는 경우(-1L) expire를 호출하여 TTL을 설정한다")
    void setsTtlWhenExpireIsMissing() {
        Long memberId = 1L;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(stringRedisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-1L);
        when(chatProperties.dailyLimit()).thenReturn(30);

        chatDailyLimitService.checkAndIncrement(memberId);

        verify(stringRedisTemplate, times(1)).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 연결 장애(DataAccessException) 발생 시 503 CHAT_SERVICE_UNAVAILABLE 예외가 발생한다")
    void checkAndIncrementThrows503WhenRedisFails() {
        Long memberId = 1L;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        assertThatThrownBy(() -> chatDailyLimitService.checkAndIncrement(memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
    }
}
