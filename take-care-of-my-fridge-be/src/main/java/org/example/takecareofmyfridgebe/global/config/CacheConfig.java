package org.example.takecareofmyfridgebe.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 공통 예외 처리 설정.
 *
 * 캐시가 켜져 있을 때(@EnableCaching)만 동작한다.
 * Spring Cache(@Cacheable·@CachePut·@CacheEvict)의 오류만 처리하고,
 * RedisTemplate·StringRedisTemplate을 직접 호출하는 코드(대화 메모리, Pub/Sub 등)의 Redis 오류는 잡지 않으므로 각 기능에서 처리해야 한다.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET error - Cache: {}, Key: {}, Message: {}",
                        cache.getName(), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error - Cache: {}, Key: {}, Message: {}",
                        cache.getName(), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT error - Cache: {}, Key: {}, Message: {}",
                        cache.getName(), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR error - Cache: {}, Message: {}",
                        cache.getName(), exception.getMessage(), exception);
            }
        };
    }
}
