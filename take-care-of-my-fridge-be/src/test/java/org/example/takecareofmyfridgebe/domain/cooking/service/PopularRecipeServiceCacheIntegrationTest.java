package org.example.takecareofmyfridgebe.domain.cooking.service;

import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.example.takecareofmyfridgebe.global.config.CacheConfig;
import org.example.takecareofmyfridgebe.global.config.CacheNames;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        RedisConfig.class,
        CacheConfig.class,
        PopularRecipeService.class
})
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        CacheAutoConfiguration.class
})
class PopularRecipeServiceCacheIntegrationTest extends RedisContainerSupport {

    @MockitoBean
    private CookingLogRepository cookingLogRepository;

    @MockitoBean
    private RecipeService recipeService;

    @MockitoBean
    private RecipeReviewService recipeReviewService;

    @Autowired
    private PopularRecipeService popularRecipeService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache(CacheNames.POPULAR_RECIPES);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("같은 size로 두 번 호출하면 집계 조회가 1회만 실행되고 Redis 캐시에서 역직렬화되어 반환된다")
    void getPopularRecipesCachesResultOnSecondCall() {
        // Given
        int size = 6;
        PopularRecipeCookingCount count = new PopularRecipeCookingCount(10L, 7L);
        when(cookingLogRepository.findPopularCookingCounts(any(), eq(size)))
                .thenReturn(List.of(count));

        RecipeSummary summary = new RecipeSummary(10L, "된장찌개", "https://img.com/10.jpg", "팁 요약");
        when(recipeService.findSummaries(List.of(10L)))
                .thenReturn(Map.of(10L, summary));

        RecipeRatingStat stat = new RecipeRatingStat(10L, 4.8, 15L);
        when(recipeReviewService.findRatingStats(List.of(10L)))
                .thenReturn(Map.of(10L, stat));

        // When: 첫 번째 호출 (캐시 미스 -> DB 조회 및 Redis 저장)
        List<PopularRecipeResponse> firstCall = popularRecipeService.getPopularRecipes(size);

        System.out.println("DEBUG_REDIS_KEYS: " + stringRedisTemplate.keys("*"));
        for (String k : stringRedisTemplate.keys("*")) {
            System.out.println("DEBUG_REDIS_KEY[" + k + "] = " + stringRedisTemplate.opsForValue().get(k));
        }
        Object cachedValue = cacheManager.getCache(CacheNames.POPULAR_RECIPES).get(size);
        System.out.println("DEBUG_CACHE_CHECK: cachedValue = " + cachedValue);

        // Then
        assertEquals(1, firstCall.size());
        assertEquals("된장찌개", firstCall.get(0).recipeName());
        assertEquals(4.8, firstCall.get(0).averageRating());
        assertEquals(15L, firstCall.get(0).reviewCount());
        verify(cookingLogRepository, times(1)).findPopularCookingCounts(any(), eq(size));

        // When: 두 번째 호출 (캐시 히트 -> Redis에서 꺼내옴)
        List<PopularRecipeResponse> secondCall = popularRecipeService.getPopularRecipes(size);

        // Then: DB 집계 메서드는 여전히 1회만 호출됨
        assertEquals(1, secondCall.size());
        assertEquals("된장찌개", secondCall.get(0).recipeName());
        assertEquals(4.8, secondCall.get(0).averageRating());
        assertEquals(15L, secondCall.get(0).reviewCount());
        verify(cookingLogRepository, times(1)).findPopularCookingCounts(any(), eq(size));
    }

    @Test
    @DisplayName("빈 결과는 unless 조건으로 인해 캐시되지 않고 다음 호출 시 다시 집계 조회를 시도한다")
    void emptyResultIsNotCachedDueToUnlessCondition() {
        // Given
        int size = 6;
        when(cookingLogRepository.findPopularCookingCounts(any(), eq(size)))
                .thenReturn(List.of());

        // When: 첫 번째 호출 (기록 없음)
        List<PopularRecipeResponse> firstCall = popularRecipeService.getPopularRecipes(size);

        // Then: 빈 결과 반환
        assertTrue(firstCall.isEmpty());
        verify(cookingLogRepository, times(1)).findPopularCookingCounts(any(), eq(size));

        // When: 두 번째 호출
        List<PopularRecipeResponse> secondCall = popularRecipeService.getPopularRecipes(size);

        // Then: 캐시되지 않았으므로 다시 repository가 호출되어 총 2회 호출됨
        assertTrue(secondCall.isEmpty());
        verify(cookingLogRepository, times(2)).findPopularCookingCounts(any(), eq(size));
    }
}
