package org.example.takecareofmyfridgebe.global.config;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Spring Cache(Redis) 설정과 공통 예외 처리.
 * <p>
 * 캐시 이름은 Redis 키 접두어가 된다: {캐시 이름}:{키} (예: recipe:classifications:all)
 * 등록하지 않은 캐시 이름은 쓸 수 없게 막아, 캐시마다 TTL과 값 타입을 여기서 정하도록 한다.
 * Spring Cache(@Cacheable, @CachePut, @CacheEvict)의 오류만 처리하고,
 * RedisTemplate, StringRedisTemplate을 직접 호출하는 코드(대화 메모리, Pub/Sub 등)의 Redis 오류는 잡지 않으므로 각 기능에서 처리해야 한다.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    // 레시피 등록, 수정 시 비우고, SQL로 직접 적재한 데이터는 TTL이 지나면 반영된다.
    private static final Duration RECIPE_CLASSIFICATIONS_TTL = Duration.ofHours(1);

    // transactionAware: @Transactional 메서드의 @CacheEvict를 커밋 이후로 미뤄,
    // 커밋 전에 다른 요청이 옛 값을 다시 캐시에 넣는 것을 막는다.
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration recipeClassifications = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(RECIPE_CLASSIFICATIONS_TTL)
                .computePrefixWith(cacheName -> cacheName + ":")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(RecipeClassificationResponse.class)));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CacheNames.RECIPE_CLASSIFICATIONS, recipeClassifications)
                .disableCreateOnMissingCache()
                .transactionAware()
                .build();
    }

    // 캐시 오류로 요청이 실패하지 않게 WARN 로그(스택트레이스 포함)만 남긴다. 캐시 조회가 실패하면 DB에서 읽는다.
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(CacheConfig.class.getName(), true);
    }
}
