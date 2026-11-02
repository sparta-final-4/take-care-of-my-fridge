package org.example.takecareofmyfridgebe.domain.chat.push;

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

// 모든 서버가 상담 채널을 구독하고, 이 서버에 연결이 있는 회원에게만 알림 SSE 연결로 event: chat을 내보낸다.
// data는 상담 번호와 상태뿐이고 메시지 내용은 싣지 않는다.
//
// 알림 구독기(NotificationPushSubscriber)와 같은 이유로, Redis에 닿지 않아도 서버가 뜨도록
// 리스너 컨테이너가 돈 다음에 따로 구독하고, 실패하면 연결될 때까지 다시 시도하며, 정지할 때 구독을 푼다
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionPushSubscriber implements MessageListener, SmartLifecycle {

    static final String EVENT_NAME = "chat";
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
            container.addMessageListener(this, new ChannelTopic(ChatSessionPushPublisher.CHANNEL));
        } catch (RuntimeException e) {
            log.warn("상담 채널 구독 실패, {}초 뒤 다시 시도: {}", RETRY_INTERVAL.toSeconds(), e.getMessage());
            retryLater();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatSessionPushMessage push = jsonMapper.readValue(message.getBody(), ChatSessionPushMessage.class);
            String data = jsonMapper.writeValueAsString(ChatSessionPushSignal.from(push));
            sseConnectionRegistry.send(push.memberId(), EVENT_NAME, data);
        } catch (RuntimeException e) {
            log.warn("상담 실시간 신호 처리 실패", e);
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
            log.debug("상담 채널 구독 해제 중 오류", e);
        }
    }
}
