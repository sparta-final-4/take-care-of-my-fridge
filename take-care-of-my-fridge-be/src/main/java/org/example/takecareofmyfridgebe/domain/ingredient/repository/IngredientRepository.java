package org.example.takecareofmyfridgebe.domain.ingredient.repository;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByName(String name);

    Optional<Ingredient> findByIngredientCode(String ingredientCode);
}
