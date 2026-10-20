package org.example.takecareofmyfridgebe.domain.review.repository;

import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeReviewRepository extends JpaRepository<RecipeReview, Long>, RecipeReviewRepositoryCustom {

    boolean existsByMemberIdAndRecipeId(Long memberId, Long recipeId);

    Optional<RecipeReview> findByIdAndMemberId(Long id, Long memberId);
}
