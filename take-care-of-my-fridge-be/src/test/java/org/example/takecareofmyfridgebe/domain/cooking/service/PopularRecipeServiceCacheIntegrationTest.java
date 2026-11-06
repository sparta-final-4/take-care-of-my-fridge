package org.example.takecareofmyfridgebe.domain.cooking.service;

import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeRanking;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        RedisConfig.class,
        CacheConfig.class,
        PopularRecipeService.class,
        PopularRecipeRankingService.class
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
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache(CacheNames.POPULAR_RECIPE_RANKING);
        if (cache != null) {
            cache.invalidate();
        }
    }

    @Test
    @DisplayName("같은 size로 두 번 호출하면 순위 집계는 1회만 하고 Redis 순위 캐시를 쓰지만, 레시피 정보·평점은 매번 읽어 바뀐 이름과 후기가 바로 반영된다")
    void cachesRankingOnly_andReadsRecipeEveryTime() {
        int size = 6;
        when(cookingLogRepository.findPopularCookingCounts(any(), eq(size)))
                .thenReturn(List.of(new PopularRecipeCookingCount(10L, 7L)));
        when(recipeService.findSummaries(List.of(10L)))
                .thenReturn(Map.of(10L, new RecipeSummary(10L, "된장찌개", "https://img.com/10.jpg", "팁 요약")))
                .thenReturn(Map.of(10L, new RecipeSummary(10L, "차돌 된장찌개", "https://img.com/10-new.jpg", "새 팁 요약")));
        when(recipeReviewService.findRatingStats(List.of(10L)))
                .thenReturn(Map.of(10L, new RecipeRatingStat(10L, 4.8, 15L)))
                .thenReturn(Map.of(10L, new RecipeRatingStat(10L, 4.5, 16L)));

        List<PopularRecipeResponse> firstCall = popularRecipeService.getPopularRecipes(size);
        List<PopularRecipeResponse> secondCall = popularRecipeService.getPopularRecipes(size);

        assertThat(firstCall).extracting(PopularRecipeResponse::recipeName).containsExactly("된장찌개");
        assertThat(firstCall.get(0).reviewCount()).isEqualTo(15L);
        assertThat(secondCall.get(0).recipeName()).isEqualTo("차돌 된장찌개");
        assertThat(secondCall.get(0).imageUrl()).isEqualTo("https://img.com/10-new.jpg");
        assertThat(secondCall.get(0).averageRating()).isEqualTo(4.5);
        assertThat(secondCall.get(0).reviewCount()).isEqualTo(16L);
        assertThat(secondCall.get(0).cookedMemberCount()).isEqualTo(7L);
        verify(cookingLogRepository, times(1)).findPopularCookingCounts(any(), eq(size));
        verify(recipeService, times(2)).findSummaries(List.of(10L));
        verify(recipeReviewService, times(2)).findRatingStats(List.of(10L));
        assertThat(cacheManager.getCache(CacheNames.POPULAR_RECIPE_RANKING).get(size, PopularRecipeRanking.class))
                .isEqualTo(new PopularRecipeRanking(List.of(new PopularRecipeCookingCount(10L, 7L))));
        assertThat(stringRedisTemplate.keys("recipe:popular::*")).isEmpty();
    }

    @Test
    @DisplayName("빈 결과는 unless 조건으로 인해 캐시되지 않고 다음 호출 시 다시 집계 조회를 시도한다")
    void doesNotCacheEmptyResult() {
        // Given
        int size = 6;
        when(cookingLogRepository.findPopularCookingCounts(any(), eq(size)))
                .thenReturn(List.of());

        // When: 첫 번째 호출 (기록 없음)
        List<PopularRecipeResponse> firstCall = popularRecipeService.getPopularRecipes(size);

        // Then: 빈 결과 반환
        assertThat(firstCall.isEmpty()).isTrue();
        verify(cookingLogRepository, times(1)).findPopularCookingCounts(any(), eq(size));

        // When: 두 번째 호출
        List<PopularRecipeResponse> secondCall = popularRecipeService.getPopularRecipes(size);

        // Then: 캐시되지 않았으므로 다시 repository가 호출되어 총 2회 호출됨
        assertThat(secondCall.isEmpty()).isTrue();
        verify(cookingLogRepository, times(2)).findPopularCookingCounts(any(), eq(size));
    }
}
