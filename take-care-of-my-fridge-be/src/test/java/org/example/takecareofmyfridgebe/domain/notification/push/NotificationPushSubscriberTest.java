package org.example.takecareofmyfridgebe.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import org.example.takecareofmyfridgebe.global.sse.SseConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

class NotificationPushSubscriberTest {

    private final RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final NotificationPushSubscriber subscriber = new NotificationPushSubscriber(
            container, mock(SseConnectionRegistry.class), JsonMapper.builder().build(), taskScheduler);

    @Test
    @DisplayName("시작해도 그 자리에서 구독하지 않고 나중에 시도하도록 맡긴다")
    void start_doesNotSubscribeImmediately() {
        subscriber.start();

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(container, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
        assertThat(subscriber.isRunning()).isTrue();
    }

    @Test
    @DisplayName("컨테이너가 돌고 있으면 알림 채널에 등록하고 다시 시도하지 않는다")
    void subscribe_registersChannel() {
        given(container.isRunning()).willReturn(true);
        subscriber.start();

        subscriber.subscribe();

        verify(container).addMessageListener(subscriber, new ChannelTopic("notification:push"));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class)); // start의 첫 시도 예약 하나뿐
    }

    @Test
    @DisplayName("Redis에 연결할 수 없어도 예외를 던지지 않고 등록을 지운 뒤 다시 시도한다")
    void subscribe_redisDown_retriesLater() {
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
    void subscribe_containerNotRunning_retriesLater() {
        given(container.isRunning()).willReturn(false);
        subscriber.start();

        subscriber.subscribe();

        verify(container, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("정지하면 구독을 풀고 이후에는 시도하지 않는다")
    void stop_removesSubscription() {
        given(container.isRunning()).willReturn(true);
        subscriber.start();

        subscriber.stop();
        subscriber.subscribe();

        verify(container).removeMessageListener(subscriber);
        verify(container, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
        assertThat(subscriber.isRunning()).isFalse();
    }

    @Test
    @DisplayName("시작하기 전에는 구독하지 않는다")
    void subscribe_beforeStart_doesNothing() {
        subscriber.subscribe();

        verifyNoInteractions(container, taskScheduler);
    }
}
