package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientNameDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepTextDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RecipeRepositoryCustom {

    Page<Recipe> findRecipesByCondition(RecipeListRequest condition, Pageable pageable);

    List<String> findDistinctCuisineTypes();

    List<String> findDistinctCategories();

    /**
     * 색인용: 레시피별 재료명,역할을 seq 순으로 조회한다. recipeId가 null이면 전체 레시피
     */
    List<RecipeIngredientNameDto> findIngredientNames(Long recipeIdOrNull);

    // recipeId가 null이면 전체 레시피. 레시피 id, step_no 순
    List<RecipeStepTextDto> findStepTexts(Long recipeId);
}
