package org.example.takecareofmyfridgebe.domain.recipe.repository;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientCountDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientNameDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientTextDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepTextDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface RecipeRepositoryCustom {

    Page<Recipe> findRecipesByCondition(RecipeListRequest condition, Pageable pageable);

    List<String> findDistinctCuisineTypes();

    List<String> findDistinctCategories();

    // 검색어 자동완성(ES 장애 대체): 이름에 keyword가 든 레시피 이름을 앞쪽에 든 것부터 limit개
    List<String> findNamesContaining(String keyword, int limit);

    /**
     * 색인용: 레시피별 재료 id·사전 이름·레시피에 적힌 이름·역할·기본 양념 여부를 seq 순으로 조회한다. recipeId가 null이면 전체 레시피
     */
    List<RecipeIngredientNameDto> findIngredientNames(Long recipeIdOrNull);

    // recipeId가 null이면 전체 레시피. 레시피 id, step_no 순
    List<RecipeStepTextDto> findStepTexts(Long recipeId);

    // AI 상담 후보 검색용: 주어진 재료가 들어간 레시피 재료 줄 (레시피·재료 쌍마다 한 줄)
    List<RecipeCandidateIngredient> findCandidateIngredients(Collection<Long> ingredientIds);

    // AI 상담 후보 검색용: 레시피별 기본 양념을 뺀 재료 종류 수 (재료가 모두 기본 양념이면 빠진다)
    List<RecipeIngredientCountDto> countNonBasicIngredients(Collection<Long> recipeIds);

    // AI 상담 후보 검색용: 레시피별 기본 양념을 뺀 재료 전체 (레시피·재료 쌍마다 한 줄, 레시피 id·레시피에 처음 나온 순)
    List<RecipeCandidateIngredient> findNonBasicIngredients(Collection<Long> recipeIds);

    // AI 상담 후보 검색용: 레시피 재료 줄마다 재료 사전 이름과 레시피에 적힌 이름 (기본 양념 포함, 매운 재료 확인용)
    List<RecipeIngredientTextDto> findIngredientTexts(Collection<Long> recipeIds);
}
