package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.search.config.QueryEmbeddingProperties;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class QueryEmbeddingServiceTest extends RedisContainerSupport {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-03T01:02:30Z"), ZoneOffset.UTC);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final RecipeEmbedder embedder = mock(RecipeEmbedder.class);

    private QueryEmbeddingService service(int limitPerMinute) {
        return new QueryEmbeddingService(embedder, redisTemplate,
                new QueryEmbeddingProperties(Duration.ofDays(1), limitPerMinute), NOW);
    }

    @BeforeEach
    void clearKeys() {
        Set<String> keys = redisTemplate.keys("search:query-embedding*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("같은 검색어는 공백·대소문자가 달라도 캐시한 벡터를 쓰고 OpenAI는 한 번만 부른다")
    void embed_reusesCachedVector_whenSameQuery() {
        // Given
        QueryEmbeddingService service = service(60);
        when(embedder.embed("칼칼한  국물")).thenReturn(new float[]{0.1f, -0.2f, 0.3f});

        // When
        Optional<float[]> first = service.embed("칼칼한  국물");
        Optional<float[]> second = service.embed(" 칼칼한 국물 ");

        // Then
        assertThat(first.orElseThrow()).containsExactly(0.1f, -0.2f, 0.3f);
        assertThat(second.orElseThrow()).containsExactly(0.1f, -0.2f, 0.3f);
        verify(embedder, times(1)).embed(anyString());
        Long ttl = redisTemplate.getExpire(QueryEmbeddingService.CACHE_KEY_PREFIX + "칼칼한 국물");
        assertThat(ttl).isNotNull().isPositive();
    }

    @Test
    @DisplayName("1분 호출 한도를 넘으면 OpenAI를 부르지 않고 빈 값을 돌려준다")
    void embed_returnsEmpty_whenOverLimit() {
        // Given
        QueryEmbeddingService service = service(2);
        when(embedder.embed(anyString())).thenReturn(new float[]{1, 0, 0});

        // When
        service.embed("하나");
        service.embed("둘");
        Optional<float[]> third = service.embed("셋");

        // Then
        assertThat(third).isEmpty();
        verify(embedder, times(2)).embed(anyString());
        verify(embedder, never()).embed("셋");
    }

    @Test
    @DisplayName("캐시에서 꺼낸 벡터는 호출 수에 세지 않는다")
    void embed_doesNotCountCall_whenCacheHit() {
        // Given
        QueryEmbeddingService service = service(1);
        when(embedder.embed("하나")).thenReturn(new float[]{1, 0, 0});
        service.embed("하나");

        // When
        Optional<float[]> cached = service.embed("하나");

        // Then
        assertThat(cached).isPresent();
        assertThat(redisTemplate.opsForValue().get(QueryEmbeddingService.LIMIT_KEY_PREFIX + "202611030102")).isEqualTo("1");
    }

    @Test
    @DisplayName("벡터를 문자열로 바꿨다가 되돌려도 값이 같다")
    void decode_returnsSameVector_whenEncoded() {
        float[] vector = {0.0123f, -1.5f, 3.4028235E38f};

        assertThat(QueryEmbeddingService.decode(QueryEmbeddingService.encode(vector))).containsExactly(vector);
    }
}
