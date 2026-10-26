package org.example.takecareofmyfridgebe.domain.recipe.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeClassificationResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RecipeListItemResponse>>> getRecipes(
            @Valid @ModelAttribute RecipeListRequest request
    ) {
        PageResponse<RecipeListItemResponse> recipes = recipeService.getRecipes(request);
        return ResponseEntity.ok(ApiResponse.ok(recipes));
    }

    @GetMapping("/classifications")
    public ResponseEntity<ApiResponse<RecipeClassificationResponse>> getClassifications() {
        RecipeClassificationResponse classifications = recipeService.getClassifications();
        return ResponseEntity.ok(ApiResponse.ok(classifications));
    }

    @GetMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipe(@PathVariable Long recipeId) {
        RecipeDetailResponse recipe = recipeService.getRecipe(recipeId);
        return ResponseEntity.ok(ApiResponse.ok(recipe));
    }
}
