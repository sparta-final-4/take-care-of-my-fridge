package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishes;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Optional;

/**
 * AI가 낸 요리 이름 목록을 Redis에 둔다. 키는 {종류 접두어}{받은 글, 공백 제거·소문자},
 * 값은 JSON 객체 {"formalName": 정식 이름 또는 null, "dishes": [요리 이름]} (SimilarDishes).
 * <ul>
 *   <li>비슷한 요리: chat:similar-dishes:v4:{요리 이름}</li>
 *   <li>넓은 요청 요리: chat:broad-dishes:v2:{요청 표현}</li>
 * </ul>
 * 같은 글이면 AI를 다시 부르지 않고 같은 목록으로 검색하게 한다 (SimilarDishFinder).
 * 유지 시간은 app.chat.similar-dishes-ttl (기본 7일)이고, 읽어도 늘리지 않는다.
 * Redis 장애면 WARN만 남기고 캐시가 없는 것처럼 동작한다 (에러로 끝내지 않음).
 * 상담 문맥(ChatContextStore)처럼 Redis 하나만 읽고 쓰는 일이라 같은 service 층에 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarDishStore {

    // 문자열 목록만 담으므로 기본 설정으로 충분하다 (ChatContextStore와 같은 방식)
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatProperties chatProperties;

    /**
     * 저장해 둔 요리 이름 목록과 정식 이름. 글이 비었거나, 없거나, 둘 다 비었거나, 읽기에 실패하면 비어 있음.
     */
    public Optional<SimilarDishes> find(Long memberId, DishListKind kind, String dishText) {
        String key = key(kind, dishText);
        if (key == null) {
            return Optional.empty();
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached == null) {
                return Optional.empty();
            }
            SimilarDishes dishes = JSON.readValue(cached, SimilarDishes.class);
            return dishes.isEmpty() ? Optional.empty() : Optional.of(dishes);
        } catch (RuntimeException e) {
            log.warn("상담 {} 캐시 읽기 실패, 캐시 없이 - memberId: {}, 글: {}, 원인: {}", kind.label(), memberId, dishText, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 요리 이름 목록과 정식 이름을 유지 시간과 함께 저장한다. 글이 비었거나 둘 다 비었으면 저장하지 않는다. 실패해도 던지지 않는다.
     * 유지 시간은 종류와 상관없이 비슷한 요리 설정(app.chat.similar-dishes-ttl)을 같이 쓴다.
     */
    public void save(Long memberId, DishListKind kind, String dishText, SimilarDishes dishes) {
        String key = key(kind, dishText);
        if (key == null || dishes == null || dishes.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, JSON.writeValueAsString(dishes), chatProperties.similarDishesTtl());
        } catch (RuntimeException e) {
            log.warn("상담 {} 캐시 저장 실패 - memberId: {}, 글: {}, 원인: {}", kind.label(), memberId, dishText, e.toString());
        }
    }

    private static String key(DishListKind kind, String dishText) {
        String compact = ChatTexts.removeWhitespace(dishText).toLowerCase(Locale.ROOT);
        return compact.isEmpty() ? null : kind.keyPrefix() + compact;
    }
}
