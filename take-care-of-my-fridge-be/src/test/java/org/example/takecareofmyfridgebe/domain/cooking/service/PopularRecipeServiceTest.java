package org.example.takecareofmyfridgebe.domain.cooking.service;

import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularRecipeServiceTest {

    @Mock
    private CookingLogRepository cookingLogRepository;

    @Mock
    private RecipeService recipeService;

    @Mock
    private RecipeReviewService recipeReviewService;

    @InjectMocks
    private PopularRecipeService popularRecipeService;

    @Test
    @DisplayName("최근 30일간 요리 기록이 없으면 추가 조회 없이 빈 목록을 반환한다")
    void returnsEmptyList_whenNoRecentCookingLogs() {
        // Given
        when(cookingLogRepository.findPopularCookingCounts(any(), anyInt()))
                .thenReturn(List.of());

        // When
        List<PopularRecipeResponse> result = popularRecipeService.getPopularRecipes(6);

        // Then
        assertThat(result.isEmpty()).isTrue();
        verify(recipeService, never()).findSummaries(any());
        verify(recipeReviewService, never()).findRatingStats(any());
    }

    @Test
    @DisplayName("인기 요리 순위와 레시피 기본정보, 후기 평점을 정상 매핑하며 후기 없는 레시피는 평점이 null, 리뷰 수가 0으로 매핑된다")
    void combinesRankRecipeAndRating() {
        // Given
        PopularRecipeCookingCount count1 = new PopularRecipeCookingCount(1L, 5L);
        PopularRecipeCookingCount count2 = new PopularRecipeCookingCount(2L, 3L);
        when(cookingLogRepository.findPopularCookingCounts(any(), anyInt()))
                .thenReturn(List.of(count1, count2));

        RecipeSummary summary1 = new RecipeSummary(1L, "된장찌개", "image1.jpg", "구수한 된장 팁");
        RecipeSummary summary2 = new RecipeSummary(2L, "김치찌개", "image2.jpg", null);
        when(recipeService.findSummaries(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, summary1, 2L, summary2));

        // recipe 1: 평점 4.5, 리뷰 10개 / recipe 2: 후기 없음
        RecipeRatingStat stat1 = new RecipeRatingStat(1L, 4.5, 10L);
        when(recipeReviewService.findRatingStats(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, stat1));

        // When
        List<PopularRecipeResponse> result = popularRecipeService.getPopularRecipes(6);

        // Then
        assertThat(result.size()).isEqualTo(2);

        PopularRecipeResponse res1 = result.get(0);
        assertThat(res1.recipeId()).isEqualTo(1L);
        assertThat(res1.recipeName()).isEqualTo("된장찌개");
        assertThat(res1.imageUrl()).isEqualTo("image1.jpg");
        assertThat(res1.tipSummary()).isEqualTo("구수한 된장 팁");
        assertThat(res1.cookedMemberCount()).isEqualTo(5L);
        assertThat(res1.averageRating()).isEqualTo(4.5);
        assertThat(res1.reviewCount()).isEqualTo(10L);

        PopularRecipeResponse res2 = result.get(1);
        assertThat(res2.recipeId()).isEqualTo(2L);
        assertThat(res2.recipeName()).isEqualTo("김치찌개");
        assertThat(res2.imageUrl()).isEqualTo("image2.jpg");
        assertThat(res2.tipSummary()).isNull();
        assertThat(res2.cookedMemberCount()).isEqualTo(3L);
        assertThat(res2.averageRating()).isNull();
        assertThat(res2.reviewCount()).isEqualTo(0L);
    }
}
