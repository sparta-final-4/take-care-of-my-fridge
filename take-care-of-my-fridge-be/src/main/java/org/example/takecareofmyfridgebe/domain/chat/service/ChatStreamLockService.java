package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// 한 상담에서 답을 동시에 하나만 받게 한다. 여러 창·여러 서버가 같은 Redis 키를 보므로 서버가 몇 대여도 맞는다.
// 잠금 값은 요청마다 새 토큰이라, 유지 시간이 지나 다른 요청이 잡은 잠금을 늦게 끝난 요청이 지우지 않는다.
// 답 생성은 트랜잭션 밖의 스트림이라 Redisson 잠금(facade에서 트랜잭션을 감싸는 용도) 대신 SET NX를 쓴다
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamLockService {

    static final String KEY_PREFIX = "chat:streaming:";

    // 값이 내 토큰일 때만 지운다. 확인과 삭제 사이에 다른 요청이 끼지 않도록 한 번에 실행한다
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatProperties chatProperties;

    /**
     * 상담의 답 받기 잠금을 잡고 해제에 쓸 토큰을 돌려준다.
     * 이미 다른 요청이 답을 받는 중이면 409(CHAT_REPLY_IN_PROGRESS), Redis 장애면 503(CHAT_SERVICE_UNAVAILABLE)을 던진다.
     *
     * @param sessionId 세션 ID
     * @return 해제할 때 넘길 토큰
     */
    public String acquire(Long sessionId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key(sessionId), token, chatProperties.replyLockTtl());
        } catch (DataAccessException e) {
            log.error("Redis 상담 답 잠금 획득 실패 (Redis 장애) - sessionId: {}", sessionId, e);
            throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
        }

        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.CHAT_REPLY_IN_PROGRESS);
        }
        return token;
    }

    /**
     * 내 토큰으로 잡은 잠금만 푼다. 실패해도 유지 시간이 지나면 풀리므로 던지지 않는다.
     *
     * @param sessionId 세션 ID
     * @param token     acquire가 돌려준 토큰
     */
    public void release(Long sessionId, String token) {
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key(sessionId)), token);
        } catch (DataAccessException e) {
            log.warn("Redis 상담 답 잠금 해제 실패, 유지 시간 뒤 풀림 - sessionId: {}", sessionId, e);
        }
    }

    private String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
