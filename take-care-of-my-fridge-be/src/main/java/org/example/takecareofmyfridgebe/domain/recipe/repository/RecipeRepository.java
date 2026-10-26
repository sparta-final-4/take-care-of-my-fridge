package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdatedAtDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, RecipeRepositoryCustom {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Recipe r SET r.tipSummary = :summary, r.updatedAt = :now WHERE r.id = :id")
    int updateTipSummary(@Param("id") Long id, @Param("summary") String summary, @Param("now") LocalDateTime now);

    @Query("SELECT new org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdatedAtDto(r.id, r.updatedAt) " +
            "FROM Recipe r WHERE r.id IN :recipeIds")
    List<RecipeUpdatedAtDto> findUpdatedAtsByIdIn(@Param("recipeIds") Collection<Long> recipeIds);
}
