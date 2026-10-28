package org.example.takecareofmyfridgebe.domain.search.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.search.facade.RecipeSearchFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final RecipeSearchFacade recipeSearchFacade;

    @GetMapping("/recipes")
    public ResponseEntity<ApiResponse<PageResponse<RecipeListItemResponse>>> searchRecipes(
            @Valid @ModelAttribute RecipeListRequest request
    ) {
        PageResponse<RecipeListItemResponse> recipes = recipeSearchFacade.getRecipes(request);
        return ResponseEntity.ok(ApiResponse.ok(recipes));
    }
}
