package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {RedisConfig.class, ChatStreamLockService.class})
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@EnableConfigurationProperties(ChatProperties.class)
class ChatStreamLockServiceTest extends RedisContainerSupport {

    private static final Long SESSION_ID = 501L;
    private static final String KEY = "chat:streaming:" + SESSION_ID;

    @Autowired
    private ChatStreamLockService chatStreamLockService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearKeys() {
        Set<String> keys = stringRedisTemplate.keys("chat:streaming:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("잠금을 잡으면 키에 토큰이 들어가고 유지 시간은 150초 이하로 걸린다")
    void storesTokenWithTtl_whenAcquired() {
        String token = chatStreamLockService.acquire(SESSION_ID);

        assertThat(stringRedisTemplate.opsForValue().get(KEY)).isEqualTo(token);
        Long ttl = stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(150L);
    }

    @Test
    @DisplayName("이미 잡힌 상담은 409 CHAT_REPLY_IN_PROGRESS, 다른 상담은 따로 잡힌다")
    void throwsInProgress_whenSameSessionIsAcquiredTwice() {
        chatStreamLockService.acquire(SESSION_ID);

        assertThatThrownBy(() -> chatStreamLockService.acquire(SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_REPLY_IN_PROGRESS);

        assertThat(chatStreamLockService.acquire(SESSION_ID + 1)).isNotBlank();
    }

    @Test
    @DisplayName("내 토큰으로 풀면 다시 잡을 수 있다")
    void allowsReacquire_whenReleasedWithOwnToken() {
        String token = chatStreamLockService.acquire(SESSION_ID);

        chatStreamLockService.release(SESSION_ID, token);

        assertThat(stringRedisTemplate.hasKey(KEY)).isFalse();
        assertThat(chatStreamLockService.acquire(SESSION_ID)).isNotEqualTo(token);
    }

    @Test
    @DisplayName("다른 토큰으로는 지우지 않는다 (유지 시간이 지나 다른 요청이 잡은 잠금 보호)")
    void keepsLock_whenReleasedWithOtherToken() {
        String token = chatStreamLockService.acquire(SESSION_ID);

        chatStreamLockService.release(SESSION_ID, "other-token");

        assertThat(stringRedisTemplate.opsForValue().get(KEY)).isEqualTo(token);
    }
}
