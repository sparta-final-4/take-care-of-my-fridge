package org.example.takecareofmyfridgebe.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.example.takecareofmyfridgebe.global.config.SseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class SseConnectionRegistryTest {

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final SseConnectionRegistry registry = new SseConnectionRegistry(
            new SseProperties(Duration.ofMinutes(30), Duration.ofSeconds(30), 2), taskScheduler);

    @Test
    @DisplayName("연결이 없는 회원에게 보내도 오류 없이 지나간다")
    void send_withoutConnection_doesNothing() {
        assertThatCode(() -> registry.send(1L, "notification", "refresh")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연결한 회원에게 보내고 하트비트를 돌려도 오류가 없고 연결이 유지된다")
    void send_withConnection_keepsConnection() {
        registry.subscribe(1L);

        registry.send(1L, "notification", "refresh");
        registry.heartbeat();

        assertThat(registry.connectionCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("회원당 연결 한도를 넘으면 가장 오래된 연결부터 빠져 한도만큼만 남는다")
    void subscribe_overLimit_keepsOnlyLimit() {
        for (int i = 0; i < 5; i++) {
            registry.subscribe(1L);
        }

        assertThat(registry.connectionCount(1L)).isEqualTo(2);
    }

    @Test
    @DisplayName("연결 한도는 회원마다 따로 센다")
    void subscribe_limitIsPerMember() {
        registry.subscribe(1L);
        registry.subscribe(1L);
        registry.subscribe(2L);

        assertThat(registry.connectionCount(1L)).isEqualTo(2);
        assertThat(registry.connectionCount(2L)).isEqualTo(1);
    }

    @Test
    @DisplayName("하트비트는 설정한 주기로 스케줄러에 등록된다")
    void scheduleHeartbeat_usesConfiguredInterval() {
        registry.scheduleHeartbeat();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(30)));
    }
}
