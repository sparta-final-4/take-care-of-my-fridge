package org.example.takecareofmyfridgebe.domain.cooking.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeRanking;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 메인 화면용 인기 레시피 조회 서비스.
 * 최근 30일간 요리 기록으로 만든 회원 수 순위는 24시간 캐시하고(PopularRecipeRankingService),
 * 레시피 이름·이미지·팁 요약과 평점·후기 수는 요청마다 읽는다. 그래서 레시피 수정·팁 요약·후기가 바로 반영된다
 * (순위 아래 레시피 조회 1번, 평점 집계 1번).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularRecipeService {

    private final PopularRecipeRankingService popularRecipeRankingService;
    private final RecipeService recipeService;
    private final RecipeReviewService recipeReviewService;

    public List<PopularRecipeResponse> getPopularRecipes(int size) {
        PopularRecipeRanking ranking = popularRecipeRankingService.getRanking(size);
        if (ranking == null || ranking.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> recipeIds = ranking.counts().stream()
                .map(PopularRecipeCookingCount::recipeId)
                .toList();

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(recipeIds);
        Map<Long, RecipeRatingStat> ratingStats = recipeReviewService.findRatingStats(recipeIds);

        List<PopularRecipeResponse> result = new ArrayList<>();
        for (PopularRecipeCookingCount count : ranking.counts()) {
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
