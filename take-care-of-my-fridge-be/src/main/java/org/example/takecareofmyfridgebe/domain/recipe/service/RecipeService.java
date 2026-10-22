package org.example.takecareofmyfridgebe.domain.recipe.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCreateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.entity.RecipeStep;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final IngredientRepository ingredientRepository;

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

    // 레시피, 재료, 조리 단계를 한 트랜잭션으로 저장한다.
    @Transactional
    public RecipeDetailResponse createRecipe(RecipeCreateRequest request) {
        validateIngredientsExist(request.ingredientIds());

        Recipe recipe = recipeRepository.save(request.toEntity(newSourceId()));
        List<RecipeIngredient> ingredients = recipeIngredientRepository.saveAll(request.toIngredients(recipe));
        List<RecipeStep> steps = recipeStepRepository.saveAll(request.toSteps(recipe));

        return RecipeDetailResponse.of(recipe, ingredients, steps);
    }

    // recipe_ingredients.ingredient_id는 FK 없이 ID로만 참조하므로 저장 전에 직접 확인한다.
    private void validateIngredientsExist(List<Long> ingredientIds) {
        if (ingredientRepository.findAllById(ingredientIds).size() != ingredientIds.size()) {
            throw new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND);
        }
    }

    // (source, source_id)가 유니크라서 관리자 등록 레시피에도 겹치지 않는 원본 코드가 필요하다. 컬럼 길이(20자)에 맞춘다.
    private static String newSourceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
