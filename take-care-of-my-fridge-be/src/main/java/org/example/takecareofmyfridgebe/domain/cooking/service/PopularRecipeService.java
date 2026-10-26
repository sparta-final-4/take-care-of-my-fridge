package org.example.takecareofmyfridgebe.domain.cooking.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.example.takecareofmyfridgebe.global.config.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 메인 화면용 인기 레시피 조회 서비스.
 * 최근 30일간의 요리 기록을 바탕으로 만든 회원 수 순으로 정렬하며,
 * 24시간 동안 캐시(Cache-Aside)된다.
 * 요리 기록이 없는 빈 결과는 캐시하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularRecipeService {

    private static final int DAYS_SPAN = 30;

    private final CookingLogRepository cookingLogRepository;
    private final RecipeService recipeService;
    private final RecipeReviewService recipeReviewService;

    @Cacheable(
            cacheNames = CacheNames.POPULAR_RECIPES,
            key = "#size",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<PopularRecipeResponse> getPopularRecipes(int size) {
        LocalDateTime since = LocalDateTime.now().minusDays(DAYS_SPAN);
        List<PopularRecipeCookingCount> cookingCounts = cookingLogRepository.findPopularCookingCounts(since, size);

        if (cookingCounts == null || cookingCounts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> recipeIds = cookingCounts.stream()
                .map(PopularRecipeCookingCount::recipeId)
                .toList();

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(recipeIds);
        Map<Long, RecipeRatingStat> ratingStats = recipeReviewService.findRatingStats(recipeIds);

        List<PopularRecipeResponse> result = new ArrayList<>();
        for (PopularRecipeCookingCount count : cookingCounts) {
            RecipeSummary summary = summaries.get(count.recipeId());
            if (summary == null) {
                continue;
            }

            RecipeRatingStat stat = ratingStats.get(count.recipeId());
            Double averageRating = stat != null ? stat.averageRating() : null;
            long reviewCount = stat != null && stat.reviewCount() != null ? stat.reviewCount() : 0L;

            result.add(new PopularRecipeResponse(
                    summary.recipeId(),
                    summary.name(),
                    summary.imageUrl(),
                    summary.tipSummary(),
                    count.cookedMemberCount(),
                    averageRating,
                    reviewCount
            ));
        }

        return result;
    }
}
