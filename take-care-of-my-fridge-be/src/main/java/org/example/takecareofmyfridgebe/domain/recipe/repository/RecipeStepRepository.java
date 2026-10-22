package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {

    List<RecipeStep> findByRecipeIdOrderByStepNoAsc(Long recipeId);

    // 목록 교체용. (recipe_id, 순서) 유니크 제약 때문에 새 행을 넣기 전에 바로 지워야 하므로
    // 플러시 순서에 맡기지 않고 벌크 삭제를 즉시 실행한다.
    @Modifying(flushAutomatically = true)
    @Query("delete from RecipeStep rs where rs.recipe.id = :recipeId")
    void deleteAllByRecipeId(@Param("recipeId") Long recipeId);
}
