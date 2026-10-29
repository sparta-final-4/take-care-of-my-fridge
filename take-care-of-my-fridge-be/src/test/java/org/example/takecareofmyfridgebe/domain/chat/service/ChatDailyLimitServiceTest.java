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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {RedisConfig.class, ChatDailyLimitService.class})
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@EnableConfigurationProperties(ChatProperties.class)
class ChatDailyLimitServiceTest extends RedisContainerSupport {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ChatDailyLimitService chatDailyLimitService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatProperties chatProperties;

    @BeforeEach
    void clearKeys() {
        var keys = stringRedisTemplate.keys("chat:limit:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("30번째 요청까지 정상 처리되고 31번째 요청 시 429 CHAT_DAILY_LIMIT_EXCEEDED 예외가 발생한다")
    void dailyLimitUpTo30SucceedsAnd31stThrowsLimitExceeded() {
        Long memberId = 1L;

        // 1부터 30까지는 정상적으로 수행됨
        for (int i = 1; i <= 30; i++) {
            chatDailyLimitService.checkAndIncrement(memberId);
        }

        // 31번째 요청 시 429 예외 발생
        assertThatThrownBy(() -> chatDailyLimitService.checkAndIncrement(memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);

        // 실패 후에도 카운트는 31로 유지 (답 생성이 실패해도 차감 유지 원칙)
        String today = ZonedDateTime.now(SEOUL_ZONE).format(DATE_FORMATTER);
        String key = "chat:limit:" + memberId + ":" + today;
        String countStr = stringRedisTemplate.opsForValue().get(key);
        assertThat(countStr).isEqualTo("31");
    }

    @Test
    @DisplayName("키 최초 생성 시 TTL이 다음 날 0시(Asia/Seoul) + 1시간 기준으로 정상 설정된다")
    void setsTtlUntilNextDayOneAmOnKeyCreation() {
        Long memberId = 2L;

        chatDailyLimitService.checkAndIncrement(memberId);

        String today = ZonedDateTime.now(SEOUL_ZONE).format(DATE_FORMATTER);
        String key = "chat:limit:" + memberId + ":" + today;

        Long expireSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(expireSeconds).isNotNull().isGreaterThan(0L);

        // 최대 25시간(=90000초) 이하, 1시간(=3600초) 이상이어야 함
        assertThat(expireSeconds).isLessThanOrEqualTo(90000L).isGreaterThanOrEqualTo(60L);
    }

    @Test
    @DisplayName("기존 키에 일시적 장애 등으로 TTL이 없는 경우(ttl < 0) 호출 시 즉시 TTL이 재설정된다")
    void resetsTtlWhenKeyHasNoExpire() {
        Long memberId = 3L;
        String today = ZonedDateTime.now(SEOUL_ZONE).format(DATE_FORMATTER);
        String key = "chat:limit:" + memberId + ":" + today;

        // TTL 없는 키 수동 생성
        stringRedisTemplate.opsForValue().set(key, "5");
        assertThat(stringRedisTemplate.getExpire(key, TimeUnit.SECONDS)).isEqualTo(-1L);

        // checkAndIncrement 호출
        chatDailyLimitService.checkAndIncrement(memberId);

        // TTL이 정상 설정되었는지 확인
        Long expireSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(expireSeconds).isNotNull().isGreaterThan(0L);
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("6");
    }
}
