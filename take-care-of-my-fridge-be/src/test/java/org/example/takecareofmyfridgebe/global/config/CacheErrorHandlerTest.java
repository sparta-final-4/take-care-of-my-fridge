package org.example.takecareofmyfridgebe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheErrorHandlerTest {

    private final CacheConfig cacheConfig = new CacheConfig();
    private final CacheErrorHandler errorHandler = cacheConfig.errorHandler();

    @Test
    void handleCacheGetErrorDoesNotThrow() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("testCache");
        RuntimeException exception = new RuntimeException("Redis connection refused");

        assertThatCode(() -> errorHandler.handleCacheGetError(exception, cache, "key1"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCachePutErrorDoesNotThrow() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("testCache");
        RuntimeException exception = new RuntimeException("Redis connection refused");

        assertThatCode(() -> errorHandler.handleCachePutError(exception, cache, "key1", "value1"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCacheEvictErrorDoesNotThrow() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("testCache");
        RuntimeException exception = new RuntimeException("Redis connection refused");

        assertThatCode(() -> errorHandler.handleCacheEvictError(exception, cache, "key1"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCacheClearErrorDoesNotThrow() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("testCache");
        RuntimeException exception = new RuntimeException("Redis connection refused");

        assertThatCode(() -> errorHandler.handleCacheClearError(exception, cache))
                .doesNotThrowAnyException();
    }
}
