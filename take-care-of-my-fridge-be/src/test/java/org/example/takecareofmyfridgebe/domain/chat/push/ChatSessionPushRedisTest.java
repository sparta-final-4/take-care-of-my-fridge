package org.example.takecareofmyfridgebe.domain.chat.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.global.config.SseProperties;
import org.example.takecareofmyfridgebe.global.sse.SseConnectionRegistry;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

// 진짜 Redis에 서버 두 대 몫의 구독기를 붙여, 한 서버가 발행한 상담 신호가 두 서버 모두에 닿는지 본다.
// 서버마다 따로 컨테이너·연결 관리자를 쓰고, 연결 관리자는 받은 신호를 확인하려고 spy로 감싼다
@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class ChatSessionPushRedisTest extends RedisContainerSupport {

    private static final long WAIT_MILLIS = 5_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SseProperties sseProperties = new SseProperties(Duration.ofMinutes(30), Duration.ofSeconds(30), 5);

    @Autowired
    ChatSessionPushRedisTest(StringRedisTemplate stringRedisTemplate, RedisConnectionFactory connectionFactory) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.connectionFactory = connectionFactory;
    }

    @Test
    @DisplayName("한 서버가 발행한 상담 신호를 두 서버가 모두 받아, 회원 번호를 뺀 상담 번호·상태만 event: chat으로 넘긴다")
    void deliversToEveryServer() {
        SseConnectionRegistry serverA = startServer();
        SseConnectionRegistry serverB = startServer();

        publisher().publish(8001L, 15L, ChatSessionStatus.COMPLETED);

        String data = "{\"sessionId\":15,\"status\":\"COMPLETED\"}";
        verify(serverA, timeout(WAIT_MILLIS)).send(8001L, "chat", data);
        verify(serverB, timeout(WAIT_MILLIS)).send(8001L, "chat", data);
    }

    @Test
    @DisplayName("서버마다 자기 연결 관리자에 그 회원 번호로만 넘기고, 다른 회원의 연결로는 보내지 않는다")
    void sendsOnlyToTargetMemberOnEachServer() {
        SseConnectionRegistry serverA = startServer();
        SseConnectionRegistry serverB = startServer();
        serverA.subscribe(8002L);
        serverB.subscribe(8003L);

        publisher().publish(8002L, 21L, ChatSessionStatus.ACTIVE);

        String data = "{\"sessionId\":21,\"status\":\"ACTIVE\"}";
        verify(serverA, timeout(WAIT_MILLIS)).send(8002L, "chat", data);
        verify(serverB, timeout(WAIT_MILLIS)).send(8002L, "chat", data);
        verify(serverA, never()).send(eq(8003L), any(), any());
        verify(serverB, never()).send(eq(8003L), any(), any());
    }

    @Test
    @DisplayName("알림 채널로 보낸 신호는 상담 구독기가 받지 않는다")
    void ignoresNotificationChannel() {
        SseConnectionRegistry server = startServer();

        stringRedisTemplate.convertAndSend("notification:push", "{\"memberId\":8004}");
        publisher().publish(8005L, 30L, ChatSessionStatus.EXPIRED);

        verify(server, timeout(WAIT_MILLIS)).send(8005L, "chat", "{\"sessionId\":30,\"status\":\"EXPIRED\"}");
        verify(server, never()).send(eq(8004L), any(), any());
    }

    @Test
    @DisplayName("깨진 메시지가 와도 구독이 죽지 않고 다음 신호를 받는다")
    void keepsSubscription_whenMessageIsBroken() {
        SseConnectionRegistry server = startServer();

        stringRedisTemplate.convertAndSend(ChatSessionPushPublisher.CHANNEL, "not-json");
        publisher().publish(8006L, 31L, ChatSessionStatus.ACTIVE);

        verify(server, timeout(WAIT_MILLIS)).send(8006L, "chat", "{\"sessionId\":31,\"status\":\"ACTIVE\"}");
    }

    private ChatSessionPushPublisher publisher() {
        return new ChatSessionPushPublisher(stringRedisTemplate, jsonMapper);
    }

    // 서버 한 대 몫: 자기 Redis 리스너 컨테이너와 연결 관리자, 구독기
    private SseConnectionRegistry startServer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        container.start();

        SseConnectionRegistry registry = spy(new SseConnectionRegistry(sseProperties, mock(TaskScheduler.class)));
        ChatSessionPushSubscriber subscriber =
                new ChatSessionPushSubscriber(container, registry, jsonMapper, mock(TaskScheduler.class));
        subscriber.start();
        subscriber.subscribe();
        return registry;
    }
}
