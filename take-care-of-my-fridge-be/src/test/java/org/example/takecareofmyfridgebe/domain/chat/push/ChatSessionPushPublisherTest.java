package org.example.takecareofmyfridgebe.domain.chat.push;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class ChatSessionPushPublisherTest {

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final ChatSessionPushPublisher publisher =
            new ChatSessionPushPublisher(stringRedisTemplate, JsonMapper.builder().build());

    @Test
    @DisplayName("상담 채널에 회원 번호·상담 번호·상태를 JSON 문자열로 발행한다")
    void publishesJsonToChatChannel() {
        publisher.publish(7L, 15L, ChatSessionStatus.COMPLETED);

        verify(stringRedisTemplate).convertAndSend("chat:push", "{\"memberId\":7,\"sessionId\":15,\"status\":\"COMPLETED\"}");
    }

    @Test
    @DisplayName("Redis 발행이 실패해도 예외를 던지지 않는다")
    void doesNotThrow_whenRedisIsDown() {
        willThrow(new RedisConnectionFailureException("down")).given(stringRedisTemplate)
                .convertAndSend(anyString(), anyString());

        assertThatCode(() -> publisher.publish(7L, 15L, ChatSessionStatus.ACTIVE)).doesNotThrowAnyException();
    }
}
