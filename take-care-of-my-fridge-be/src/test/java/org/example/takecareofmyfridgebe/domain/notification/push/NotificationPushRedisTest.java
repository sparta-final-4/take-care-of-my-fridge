package org.example.takecareofmyfridgebe.domain.notification.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
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

// 진짜 Redis에 서버 두 대 몫의 구독기를 붙여, 한 서버가 발행한 신호가 두 서버 모두에 닿는지 본다.
// 서버마다 따로 컨테이너·연결 관리자를 쓰고, 연결 관리자는 받은 신호를 확인하려고 spy로 감싼다
@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class NotificationPushRedisTest extends RedisContainerSupport {

    private static final long WAIT_MILLIS = 5_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SseProperties sseProperties = new SseProperties(Duration.ofMinutes(30), Duration.ofSeconds(30), 5);

    @Autowired
    NotificationPushRedisTest(StringRedisTemplate stringRedisTemplate, RedisConnectionFactory connectionFactory) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.connectionFactory = connectionFactory;
    }

    @Test
    @DisplayName("한 서버가 발행한 신호를 구독 중인 두 서버가 모두 받아 연결 관리자에 넘긴다")
    void publish_reachesEveryServer() {
        SseConnectionRegistry serverA = startServer();
        SseConnectionRegistry serverB = startServer();

        new NotificationPushPublisher(stringRedisTemplate, jsonMapper).publish(7001L);

        verify(serverA, timeout(WAIT_MILLIS)).send(7001L, "notification", "refresh");
        verify(serverB, timeout(WAIT_MILLIS)).send(7001L, "notification", "refresh");
    }

    @Test
    @DisplayName("발행한 회원 번호만 전달하고 다른 회원 번호로는 보내지 않는다")
    void publish_carriesOnlyThatMember() {
        SseConnectionRegistry server = startServer();

        new NotificationPushPublisher(stringRedisTemplate, jsonMapper).publish(7002L);

        verify(server, timeout(WAIT_MILLIS)).send(7002L, "notification", "refresh");
        verify(server, never()).send(eq(7003L), any(), any());
    }

    @Test
    @DisplayName("깨진 메시지가 와도 구독이 죽지 않고 다음 신호를 받는다")
    void brokenMessage_doesNotKillSubscription() {
        SseConnectionRegistry server = startServer();

        stringRedisTemplate.convertAndSend(NotificationPushPublisher.CHANNEL, "not-json");
        new NotificationPushPublisher(stringRedisTemplate, jsonMapper).publish(7004L);

        verify(server, timeout(WAIT_MILLIS)).send(7004L, "notification", "refresh");
    }

    // 서버 한 대 몫: 자기 Redis 리스너 컨테이너와 연결 관리자, 구독기
    private SseConnectionRegistry startServer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        container.start();

        SseConnectionRegistry registry = spy(new SseConnectionRegistry(sseProperties, mock(TaskScheduler.class)));
        NotificationPushSubscriber subscriber =
                new NotificationPushSubscriber(container, registry, jsonMapper, mock(TaskScheduler.class));
        subscriber.start();
        subscriber.subscribe();
        return registry;
    }
}
