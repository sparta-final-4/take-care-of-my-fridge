package org.example.takecareofmyfridgebe.domain.ingredient.repository;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findByNameIn(Collection<String> names);

    // 이름이 suffix로 끝나는 재료 (LIKE '%suffix', 특수 문자는 Spring Data가 이스케이프)
    List<Ingredient> findByNameEndingWith(String suffix);

    Optional<Ingredient> findByIngredientCode(String ingredientCode);
}
