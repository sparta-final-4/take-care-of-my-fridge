package org.example.takecareofmyfridgebe.domain.recipe.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeIngredientRepository;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeRepository;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeStepRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeStepRepository recipeStepRepository;

    public PageResponse<RecipeListItemResponse> getRecipes(RecipeSort sort, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, sort.toSort());

        Page<RecipeListItemResponse> response = recipeRepository.findAll(pageable)
                .map(RecipeListItemResponse::from);

        return PageResponse.of(response);
    }

    public RecipeDetailResponse getRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));

        return RecipeDetailResponse.of(
                recipe,
                recipeIngredientRepository.findByRecipeIdOrderBySeqAsc(recipeId),
                recipeStepRepository.findByRecipeIdOrderByStepNoAsc(recipeId)
        );
    }
}
