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

    // 검색어 자동완성(ES 장애 대체): 이름에 keyword가 든 레시피 이름을 앞쪽에 든 것부터 limit개
    List<String> findNamesContaining(String keyword, int limit);

    /**
     * 색인용: 레시피별 재료명,역할을 seq 순으로 조회한다. recipeId가 null이면 전체 레시피
     */
    List<RecipeIngredientNameDto> findIngredientNames(Long recipeIdOrNull);

    // recipeId가 null이면 전체 레시피. 레시피 id, step_no 순
    List<RecipeStepTextDto> findStepTexts(Long recipeId);
}
