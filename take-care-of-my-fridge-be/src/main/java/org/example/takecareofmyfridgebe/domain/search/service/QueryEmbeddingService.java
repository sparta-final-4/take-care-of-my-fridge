package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.config.QueryEmbeddingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 벡터 폴백용 검색어 임베딩. 0건 검색어마다 OpenAI를 부르지 않도록 캐시와 호출 제한을 둔다.
 * <br>
 * 1) 같은 검색어(공백·대소문자만 다른 것 포함)는 Redis에 둔 벡터를 다시 쓴다
 * 2) 캐시에 없으면 서비스 전체에서 1분에 limitPerMinute번까지만 OpenAI를 부른다. 넘으면 빈 값을 돌려준다
 * <br>
 * 비로그인도 쓰는 API라 IP별이 아니라 전체 호출 수로 막는다. 넘은 요청은 벡터 폴백이 없던 때처럼 0건이 된다.
 * Redis 장애면 비용이 늘지 않도록 OpenAI를 부르지 않고 빈 값을 돌려준다.
 */
@Slf4j
@Service
public class QueryEmbeddingService {

    static final String CACHE_KEY_PREFIX = "search:query-embedding:";
    static final String LIMIT_KEY_PREFIX = "search:query-embedding-limit:";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    // 분이 바뀐 뒤에도 늦게 온 increment가 새 키를 만들지 않도록 1분보다 길게 둔다
    private static final Duration LIMIT_KEY_TTL = Duration.ofMinutes(2);

    private final RecipeEmbedder embedder;
    private final StringRedisTemplate redisTemplate;
    private final QueryEmbeddingProperties properties;
    private final Clock clock;

    @Autowired
    public QueryEmbeddingService(RecipeEmbedder embedder, StringRedisTemplate redisTemplate, QueryEmbeddingProperties properties) {
        this(embedder, redisTemplate, properties, Clock.systemUTC());
    }

    // 테스트에서 시각을 정해 넣을 때 쓴다
    QueryEmbeddingService(RecipeEmbedder embedder, StringRedisTemplate redisTemplate, QueryEmbeddingProperties properties, Clock clock) {
        this.embedder = embedder;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<float[]> embed(String keyword) {
        String cacheKey = CACHE_KEY_PREFIX + normalize(keyword);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Optional.of(decode(cached));
            }
            if (!acquire()) {
                log.warn("검색어 임베딩 호출 제한 초과, 벡터 폴백 생략: keyword={}", keyword);
                return Optional.empty();
            }
        } catch (DataAccessException e) {
            log.warn("검색어 임베딩 캐시 조회 실패, 벡터 폴백 생략: keyword={}", keyword, e);
            return Optional.empty();
        }

        float[] vector = embedder.embed(keyword);
        try {
            redisTemplate.opsForValue().set(cacheKey, encode(vector), properties.cacheTtl());
        } catch (DataAccessException e) {
            // 이번 응답에는 벡터가 있으므로 저장 실패는 다음 요청이 다시 부르는 것으로 끝난다
            log.warn("검색어 임베딩 캐시 저장 실패: keyword={}", keyword, e);
        }
        return Optional.of(vector);
    }

    // 이번 분의 호출 수를 하나 늘리고, 한도 안이면 true
    private boolean acquire() {
        String key = LIMIT_KEY_PREFIX + LocalDateTime.now(clock).format(MINUTE);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LIMIT_KEY_TTL);
        }
        return count != null && count <= properties.limitPerMinute();
    }

    // "김치  찌개 ", "김치 찌개"는 같은 키. 임베딩에는 사용자가 입력한 검색어를 그대로 보낸다
    static String normalize(String keyword) {
        return WHITESPACE.matcher(keyword.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    static String encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.asFloatBuffer().put(vector);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    static float[] decode(String value) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
        float[] vector = new float[buffer.remaining() / Float.BYTES];
        buffer.asFloatBuffer().get(vector);
        return vector;
    }

}
