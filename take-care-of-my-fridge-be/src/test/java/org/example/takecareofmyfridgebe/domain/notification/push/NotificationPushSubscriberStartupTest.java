package org.example.takecareofmyfridgebe.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.global.config.SseProperties;
import org.example.takecareofmyfridgebe.global.sse.SseConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

// Redis에 닿지 않아도 서버가 떠야 한다. CI처럼 Redis가 없는 곳에서 앱 전체가 뜨는 테스트가 깨지지 않게 하는 약속이다.
// 아무도 듣지 않는 포트를 가리켜 Redis가 없는 상황을 만든다
@SpringBootTest(
        classes = {
                RedisConfig.class,
                NotificationPushSubscriber.class,
                SseConnectionRegistry.class,
                NotificationPushSubscriberStartupTest.TestConfig.class
        },
        properties = {
                "spring.data.redis.port=6390",
                "spring.data.redis.connect-timeout=500ms",
                "spring.data.redis.timeout=500ms"
        })
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class NotificationPushSubscriberStartupTest {

    private final ApplicationContext context;

    @Autowired
    NotificationPushSubscriberStartupTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    @DisplayName("Redis에 연결할 수 없어도 알림 구독기를 포함해 서버가 뜬다")
    void contextStarts_withoutRedis() {
        assertThat(context.getBean(NotificationPushSubscriber.class)).isNotNull();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        SseProperties sseProperties() {
            return new SseProperties(Duration.ofMinutes(30), Duration.ofSeconds(30), 5);
        }

        @Bean
        TaskScheduler taskScheduler() {
            return mock(TaskScheduler.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
