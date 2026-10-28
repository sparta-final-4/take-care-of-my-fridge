package org.example.takecareofmyfridgebe.domain.recipe.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIndexSource;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIngredientNameDto;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeStepTextDto;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeIndexSourceService {

    private final RecipeRepository recipeRepository;

    /**
     * 레시피 전체를 색인용 DTO로 변환. 레시피, 재료, 조리순서 모두 조회
     * @return 색인용 DTO 목록
     */
    public List<RecipeIndexSource> findAll() {
        Map<Long, List<RecipeIndexSource.Ingredient>> ingredientsByRecipe =
                groupIngredients(recipeRepository.findIngredientNames(null));
        Map<Long, List<String>> stepsByRecipe = groupSteps(recipeRepository.findStepTexts(null));

        return recipeRepository.findAll(Sort.by("id")).stream()
                .map(recipe -> toSource(recipe, ingredientsByRecipe, stepsByRecipe))
                .toList();
    }

    /**
     * 단건 색인용. 삭제된 레시피면 빈 Optional
     * @return 색인용 DTO
     */
    public Optional<RecipeIndexSource> findOne(long recipeId) {
        return recipeRepository.findById(recipeId)
                .map(recipe -> toSource(recipe,
                        groupIngredients(recipeRepository.findIngredientNames(recipeId)),
                        groupSteps(recipeRepository.findStepTexts(recipeId))
                ));
    }

    private RecipeIndexSource toSource(
            Recipe recipe,
            Map<Long, List<RecipeIndexSource.Ingredient>> ingredientsByRecipe,
            Map<Long, List<String>> stepsByRecipe
    ) {
        return RecipeIndexSource.from(recipe,
                ingredientsByRecipe.getOrDefault(recipe.getId(), List.of()),
                stepsByRecipe.getOrDefault(recipe.getId(), List.of()));
    }

    private Map<Long, List<RecipeIndexSource.Ingredient>> groupIngredients(List<RecipeIngredientNameDto> rows) {
        Map<Long, List<RecipeIndexSource.Ingredient>> result = new HashMap<>();
        for (RecipeIngredientNameDto row : rows) {
            result.computeIfAbsent(row.recipeId(), k -> new ArrayList<>())
                    .add(new RecipeIndexSource.Ingredient(row.name(), row.role()));
        }
        return result;
    }

    private Map<Long, List<String>> groupSteps(List<RecipeStepTextDto> rows) {
        Map<Long, List<String>> result = new HashMap<>();
        for (RecipeStepTextDto row : rows) {
            result.computeIfAbsent(row.recipeId(), k -> new ArrayList<>()).add(row.description());
        }
        return result;
    }

}
