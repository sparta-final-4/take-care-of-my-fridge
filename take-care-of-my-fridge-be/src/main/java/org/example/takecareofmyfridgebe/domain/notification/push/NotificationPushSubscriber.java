package org.example.takecareofmyfridgebe.domain.notification.push;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.sse.SseConnectionRegistry;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 모든 서버가 알림 채널을 구독하고, 이 서버에 연결이 있는 회원에게만 내용 없는 신호를 SSE로 내보낸다.
// 내용을 싣지 않으므로 로그아웃 뒤 남은 연결로도 알림 내용이 새지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushSubscriber implements MessageListener {

    static final String EVENT_NAME = "notification";
    static final String SIGNAL = "refresh";

    private final RedisMessageListenerContainer container;
    private final SseConnectionRegistry sseConnectionRegistry;
    private final JsonMapper jsonMapper;

    @PostConstruct
    void subscribe() {
        container.addMessageListener(this, new ChannelTopic(NotificationPushPublisher.CHANNEL));
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
}
