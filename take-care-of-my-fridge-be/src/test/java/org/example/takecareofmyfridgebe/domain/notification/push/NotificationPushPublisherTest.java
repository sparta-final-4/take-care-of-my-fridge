package org.example.takecareofmyfridgebe.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class NotificationPushPublisherTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final NotificationPushPublisher publisher =
            new NotificationPushPublisher(redisTemplate, JsonMapper.builder().build());

    @Test
    @DisplayName("알림 채널에 회원 번호만 담아 발행한다")
    void publish_sendsOnlyMemberId() {
        publisher.publish(7L);

        verify(redisTemplate).convertAndSend("notification:push", "{\"memberId\":7}");
    }

    @Test
    @DisplayName("Redis 발행이 실패해도 예외를 던지지 않는다")
    void publish_redisFailure_doesNotThrow() {
        willThrow(new RedisConnectionFailureException("down")).given(redisTemplate).convertAndSend(anyString(), anyString());

        assertThatCode(() -> publisher.publish(7L)).doesNotThrowAnyException();
    }
}
