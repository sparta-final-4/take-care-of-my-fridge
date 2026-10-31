package org.example.takecareofmyfridgebe.domain.notification.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 알림이 저장됐다는 신호를 모든 서버에 알린다. 알림은 이미 DB에 남았으므로 발행이 실패해도 다시 던지지 않는다.
// 놓친 신호는 다음 조회 때 알림이 보이는 것으로 메워진다
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushPublisher {

    static final String CHANNEL = "notification:push";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    public void publish(Long memberId) {
        try {
            String payload = jsonMapper.writeValueAsString(new NotificationPushMessage(memberId));
            stringRedisTemplate.convertAndSend(CHANNEL, payload);
        } catch (RuntimeException e) {
            log.warn("알림 실시간 신호 발행 실패: memberId={}", memberId, e);
        }
    }
}
