package org.example.takecareofmyfridgebe.domain.notification.push;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.sse.SseConnectionRegistry;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 모든 서버가 알림 채널을 구독하고, 이 서버에 연결이 있는 회원에게만 내용 없는 신호를 SSE로 내보낸다.
// 내용을 싣지 않으므로 로그아웃 뒤 남은 연결로도 알림 내용이 새지 않는다.
//
// Redis에 닿지 않아도 서버가 떠야 하므로 구독은 Redis 리스너 컨테이너가 돈 다음에 따로 시도한다.
// 컨테이너가 시작할 때 이미 등록된 리스너가 있으면 그 자리에서 Redis에 접속하다 서버 시작이 실패한다.
// 정지할 때는 구독을 풀어 두어, 같은 컨텍스트를 다시 시작해도(테스트 컨텍스트 재사용 등) 같은 일이 생기지 않게 한다.
// 구독에 실패하면 연결될 때까지 다시 시도한다
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushSubscriber implements MessageListener, SmartLifecycle {

    static final String EVENT_NAME = "notification";
    static final String SIGNAL = "refresh";
    private static final Duration FIRST_ATTEMPT_DELAY = Duration.ofSeconds(1);
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);

    private final RedisMessageListenerContainer container;
    private final SseConnectionRegistry sseConnectionRegistry;
    private final JsonMapper jsonMapper;
    private final TaskScheduler taskScheduler;

    private volatile boolean running;

    @Override
    public void start() {
        running = true;
        taskScheduler.schedule(this::subscribe, Instant.now().plus(FIRST_ATTEMPT_DELAY));
    }

    @Override
    public void stop() {
        running = false;
        removeSubscription();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void subscribe() {
        if (!running) {
            return;
        }
        if (!container.isRunning()) {
            retryLater();
            return;
        }
        try {
            container.addMessageListener(this, new ChannelTopic(NotificationPushPublisher.CHANNEL));
        } catch (RuntimeException e) {
            log.warn("알림 채널 구독 실패, {}초 뒤 다시 시도: {}", RETRY_INTERVAL.toSeconds(), e.getMessage());
            retryLater();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationPushMessage push = jsonMapper.readValue(message.getBody(), NotificationPushMessage.class);
            sseConnectionRegistry.send(push.memberId(), EVENT_NAME, SIGNAL);
        } catch (RuntimeException e) {
            log.warn("알림 실시간 신호 처리 실패", e);
        }
    }

    // 실패한 시도가 남긴 등록을 지우고 다시 시도한다
    private void retryLater() {
        removeSubscription();
        if (running) {
            taskScheduler.schedule(this::subscribe, Instant.now().plus(RETRY_INTERVAL));
        }
    }

    private void removeSubscription() {
        try {
            container.removeMessageListener(this);
        } catch (RuntimeException e) {
            log.debug("알림 채널 구독 해제 중 오류", e);
        }
    }
}
