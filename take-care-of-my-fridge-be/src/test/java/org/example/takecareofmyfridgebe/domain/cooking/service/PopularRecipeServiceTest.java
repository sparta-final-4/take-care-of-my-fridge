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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void getPopularRecipesReturnsEmptyListWhenNoCookingLogs() {
        // Given
        when(cookingLogRepository.findPopularCookingCounts(any(), anyInt()))
                .thenReturn(List.of());

        // When
        List<PopularRecipeResponse> result = popularRecipeService.getPopularRecipes(6);

        // Then
        assertTrue(result.isEmpty());
        verify(recipeService, never()).findSummaries(any());
        verify(recipeReviewService, never()).findRatingStats(any());
    }

    @Test
    @DisplayName("인기 요리 순위와 레시피 기본정보, 후기 평점을 정상 매핑하며 후기 없는 레시피는 평점이 null, 리뷰 수가 0으로 매핑된다")
    void getPopularRecipesCombinesAllDataCorrectly() {
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
        assertEquals(2, result.size());

        PopularRecipeResponse res1 = result.get(0);
        assertEquals(1L, res1.recipeId());
        assertEquals("된장찌개", res1.recipeName());
        assertEquals("image1.jpg", res1.imageUrl());
        assertEquals("구수한 된장 팁", res1.tipSummary());
        assertEquals(5L, res1.cookedMemberCount());
        assertEquals(4.5, res1.averageRating());
        assertEquals(10L, res1.reviewCount());

        PopularRecipeResponse res2 = result.get(1);
        assertEquals(2L, res2.recipeId());
        assertEquals("김치찌개", res2.recipeName());
        assertEquals("image2.jpg", res2.imageUrl());
        assertNull(res2.tipSummary());
        assertEquals(3L, res2.cookedMemberCount());
        assertNull(res2.averageRating());
        assertEquals(0L, res2.reviewCount());
    }
}
