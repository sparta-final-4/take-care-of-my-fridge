package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeTasteDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeTaste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeTasteRepository extends JpaRepository<RecipeTaste, Long> {

    @Query("SELECT new org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeTasteDto(rt.recipe.id, rt.taste) " +
            "FROM RecipeTaste rt WHERE rt.recipe.id IN :recipeIds")
    List<RecipeTasteDto> findTastesByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
}
