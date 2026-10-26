package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RecipeRepositoryCustom {

    Page<Recipe> findRecipesByCondition(RecipeListRequest condition, Pageable pageable);

    List<String> findDistinctCuisineTypes();

    List<String> findDistinctCategories();
}
