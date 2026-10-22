package org.example.takecareofmyfridgebe.domain.recipe.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCreateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeUpdateRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recipes")
@RequiredArgsConstructor
public class AdminRecipeController {

    private final RecipeService recipeService;

    @PostMapping
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> createRecipe(
            @Valid @RequestBody RecipeCreateRequest request
    ) {
        RecipeDetailResponse recipe = recipeService.createRecipe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(recipe));
    }

    @PatchMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> updateRecipe(
            @PathVariable Long recipeId,
            @Valid @RequestBody RecipeUpdateRequest request
    ) {
        RecipeDetailResponse recipe = recipeService.updateRecipe(recipeId, request);
        return ResponseEntity.ok(ApiResponse.ok(recipe));
    }
}
