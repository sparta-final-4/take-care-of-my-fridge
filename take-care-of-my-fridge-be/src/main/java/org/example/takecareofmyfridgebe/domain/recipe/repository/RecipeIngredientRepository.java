package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    List<RecipeIngredient> findByRecipeIdOrderBySeqAsc(Long recipeId);
}
