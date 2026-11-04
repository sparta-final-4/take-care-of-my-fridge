package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/**
 * 상담의 직전 추천(조건·카드 레시피 ID)을 Redis에 둔다. 키는 chat:context:{sessionId}, 값은 JSON.
 * 유지 시간은 상담 만료 시간(app.chat.session-timeout, 30분)과 같고, 답을 저장할 때마다 다시 늘린다.
 * Redis 장애면 WARN만 남기고 문맥 없이 지금처럼 동작한다 (에러로 끝내지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContextStore {

    static final String KEY_PREFIX = "chat:context:";
    // 레코드만 담으므로 기본 설정으로 충분하다 (스프링 ObjectMapper 설정과 상관없이 같은 모양)
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatProperties chatProperties;

    /**
     * 추천 답(새 추천·다시 추천)을 저장한 뒤 그때 조건과 카드를 직전 추천으로 둔다.
     */
    public void saveRecommendation(Long sessionId, RecipeQuery query, List<Long> recipeIds) {
        try {
            String value = JSON.writeValueAsString(LastRecommendation.of(query, recipeIds));
            stringRedisTemplate.opsForValue().set(key(sessionId), value, chatProperties.sessionTimeout());
        } catch (RuntimeException e) {
            log.warn("상담 직전 추천 저장 실패, 다음 말은 문맥 없이 처리 - sessionId: {}, 원인: {}", sessionId, e.toString());
        }
    }

    public Optional<LastRecommendation> find(Long sessionId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key(sessionId));
            return value == null ? Optional.empty() : Optional.of(JSON.readValue(value, LastRecommendation.class));
        } catch (RuntimeException e) {
            log.warn("상담 직전 추천 읽기 실패, 문맥 없이 계속 - sessionId: {}, 원인: {}", sessionId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 추천이 아닌 답(일반·레시피 자세히)을 저장한 뒤 직전 추천은 그대로 두고 유지 시간만 다시 늘린다.
     */
    public void touch(Long sessionId) {
        try {
            stringRedisTemplate.expire(key(sessionId), chatProperties.sessionTimeout());
        } catch (RuntimeException e) {
            log.warn("상담 직전 추천 유지 시간 늘리기 실패 - sessionId: {}, 원인: {}", sessionId, e.toString());
        }
    }

    private static String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
