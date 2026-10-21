package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {

    List<RecipeStep> findByRecipeIdOrderByStepNoAsc(Long recipeId);
}
