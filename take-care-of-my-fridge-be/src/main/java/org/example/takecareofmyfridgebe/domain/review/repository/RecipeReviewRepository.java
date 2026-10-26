package org.example.takecareofmyfridgebe.domain.review.repository;

import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecipeReviewRepository extends JpaRepository<RecipeReview, Long>, RecipeReviewRepositoryCustom {

    boolean existsByMemberIdAndRecipeId(Long memberId, Long recipeId);

    Optional<RecipeReview> findByIdAndMemberId(Long id, Long memberId);

    @Query("SELECT new org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat(" +
            "r.recipeId, AVG(r.rating), COUNT(r.id)) " +
            "FROM RecipeReview r " +
            "WHERE r.recipeId IN :recipeIds " +
            "GROUP BY r.recipeId")
    List<RecipeRatingStat> findRatingStatsByRecipeIds(@Param("recipeIds") Collection<Long> recipeIds);
}
