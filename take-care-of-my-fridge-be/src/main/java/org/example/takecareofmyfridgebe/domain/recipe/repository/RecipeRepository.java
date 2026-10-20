package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
