package org.example.takecareofmyfridgebe.domain.chat.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.example.takecareofmyfridgebe.global.sse.SseConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

class ChatSessionPushSubscriberTest {

    private final RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
    private final SseConnectionRegistry sseConnectionRegistry = mock(SseConnectionRegistry.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ChatSessionPushSubscriber subscriber = new ChatSessionPushSubscriber(
            container, sseConnectionRegistry, JsonMapper.builder().build(), taskScheduler);

    @Test
    @DisplayName("받은 신호를 그 회원 연결에 event: chat으로 넘기고, data는 회원 번호를 뺀 상담 번호·상태 JSON이다")
    void sendsChatEventWithoutMemberId() {
        subscriber.onMessage(message("{\"memberId\":7,\"sessionId\":15,\"status\":\"COMPLETED\"}"), null);

        verify(sseConnectionRegistry).send(7L, "chat", "{\"sessionId\":15,\"status\":\"COMPLETED\"}");
    }

    @Test
    @DisplayName("깨진 메시지는 로그만 남기고 연결 관리자를 부르지 않는다")
    void ignoresMessage_whenMessageIsBroken() {
        subscriber.onMessage(message("not-json"), null);

        verifyNoInteractions(sseConnectionRegistry);
    }

    @Test
    @DisplayName("시작해도 그 자리에서 구독하지 않고 나중에 시도하도록 맡긴다")
    void defersSubscription_whenStarted() {
        subscriber.start();

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(container, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
        assertThat(subscriber.isRunning()).isTrue();
    }

    @Test
    @DisplayName("컨테이너가 돌고 있으면 상담 채널에 등록하고 다시 시도하지 않는다")
    void registersChannel_whenContainerIsRunning() {
        given(container.isRunning()).willReturn(true);
        subscriber.start();

        subscriber.subscribe();

        verify(container).addMessageListener(subscriber, new ChannelTopic("chat:push"));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class)); // start의 첫 시도 예약 하나뿐
    }

    @Test
    @DisplayName("Redis에 연결할 수 없어도 예외를 던지지 않고 등록을 지운 뒤 다시 시도한다")
    void retriesLater_whenRedisIsDown() {
        given(container.isRunning()).willReturn(true);
        willThrow(new RedisConnectionFailureException("down")).given(container)
                .addMessageListener(any(MessageListener.class), any(Topic.class));
        subscriber.start();

        subscriber.subscribe();

        verify(container).removeMessageListener(subscriber);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("컨테이너가 아직 돌지 않으면 등록하지 않고 다시 시도한다")
    void retriesLater_whenContainerIsNotRunning() {
        given(container.isRunning()).willReturn(false);
        subscriber.start();

        subscriber.subscribe();

        verify(container, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("정지하면 구독을 풀고 이후에는 시도하지 않는다")
    void removesSubscription_whenStopped() {
        given(container.isRunning()).willReturn(true);
        subscriber.start();

        subscriber.stop();
        subscriber.subscribe();

        verify(container).removeMessageListener(subscriber);
        verify(container, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
        assertThat(subscriber.isRunning()).isFalse();
    }

    private DefaultMessage message(String body) {
        return new DefaultMessage("chat:push".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
