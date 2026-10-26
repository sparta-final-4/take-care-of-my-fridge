package org.example.takecareofmyfridgebe.domain.review.dto;

/**
 * 레시피별 별점 통계 (평균 평점 및 리뷰 수).
 * JPQL 생성자 프로젝션(AVG, COUNT) 결과를 받아
 * 평균 평점을 소수 첫째 자리로 반올림하고 null 리뷰 수를 0L로 보정한다.
 */
public record RecipeRatingStat(
        Long recipeId,
        Double averageRating,
        Long reviewCount
) {
    public RecipeRatingStat(Long recipeId, Double averageRating, Long reviewCount) {
        this.recipeId = recipeId;
        this.averageRating = averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : null;
        this.reviewCount = reviewCount != null ? reviewCount : 0L;
    }
}
