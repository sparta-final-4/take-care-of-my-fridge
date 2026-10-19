package org.example.takecareofmyfridgebe.domain.ingredient.repository;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findByNameIn(Collection<String> names);

    Optional<Ingredient> findByIngredientCode(String ingredientCode);
}
