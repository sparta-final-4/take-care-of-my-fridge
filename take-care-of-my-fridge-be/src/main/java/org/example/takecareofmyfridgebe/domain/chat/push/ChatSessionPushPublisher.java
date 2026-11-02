package org.example.takecareofmyfridgebe.domain.chat.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 상담이 바뀌었다는 신호를 모든 서버에 알린다. 상담은 이미 DB에 남았으므로 발행이 실패해도 다시 던지지 않는다.
// 놓친 신호는 화면이 다음에 상담을 다시 조회할 때 메워진다
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionPushPublisher {

    static final String CHANNEL = "chat:push";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    public void publish(Long memberId, Long sessionId, ChatSessionStatus status) {
        try {
            String payload = jsonMapper.writeValueAsString(new ChatSessionPushMessage(memberId, sessionId, status));
            stringRedisTemplate.convertAndSend(CHANNEL, payload);
        } catch (RuntimeException e) {
            log.warn("상담 실시간 신호 발행 실패: memberId={}, sessionId={}, status={}", memberId, sessionId, status, e);
        }
    }
}
