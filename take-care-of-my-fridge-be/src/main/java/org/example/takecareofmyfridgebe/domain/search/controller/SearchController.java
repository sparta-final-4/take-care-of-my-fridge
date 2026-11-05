package org.example.takecareofmyfridgebe.domain.search.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.PopularKeywordsResponse;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchResponse;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchRecipeClickRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchSuggestionRequest;
import org.example.takecareofmyfridgebe.domain.search.facade.ProductSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.facade.RecipeSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.facade.SearchFridgeMatchFacade;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickHistoryService;
import org.example.takecareofmyfridgebe.domain.search.service.SearchPopularKeywordService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final RecipeSearchFacade recipeSearchFacade;
    private final ProductSearchFacade productSearchFacade;
    private final SearchClickHistoryService searchClickHistoryService;
    private final SearchFridgeMatchFacade searchFridgeMatchFacade;
    private final SearchPopularKeywordService searchPopularKeywordService;

    @GetMapping("/recipes")
    public ResponseEntity<ApiResponse<PageResponse<RecipeListItemResponse>>> searchRecipes(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @ModelAttribute RecipeListRequest request
    ) {
        PageResponse<RecipeListItemResponse> recipes =
                recipeSearchFacade.getRecipes(request, principal == null ? null : principal.memberId());
        return ResponseEntity.ok(ApiResponse.ok(recipes));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductListItemResponse>>> searchProducts(
            @Valid @ModelAttribute ProductListRequest request
    ) {
        PageResponse<ProductListItemResponse> products = productSearchFacade.getProducts(request);
        return ResponseEntity.ok(ApiResponse.ok(products));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> suggest(
            @Valid @ModelAttribute SearchSuggestionRequest request
    ) {
        List<String> suggestions = recipeSearchFacade.suggestNames(request.keyword());
        return ResponseEntity.ok(ApiResponse.ok(suggestions));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PopularKeywordsResponse>> getPopularKeywords() {
        PopularKeywordsResponse keywords = searchPopularKeywordService.getTopKeywords();
        return ResponseEntity.ok(ApiResponse.ok(keywords));
    }

    // 로그인 회원만 (SecurityConfig)
    @GetMapping("/fridge-matches")
    public ResponseEntity<ApiResponse<SearchFridgeMatchResponse>> matchFridge(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @ModelAttribute SearchFridgeMatchRequest request
    ) {
        SearchFridgeMatchResponse response = searchFridgeMatchFacade.match(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/clicks")
    public ResponseEntity<ApiResponse<Void>> recordClick(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @RequestBody SearchClickRequest request
    ) {
        searchClickHistoryService.record(request, principal == null ? null : principal.memberId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/recipe-clicks")
    public ResponseEntity<ApiResponse<Void>> recordRecipeClick(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @RequestBody SearchRecipeClickRequest request
    ) {
        searchClickHistoryService.recordRecipe(request, principal == null ? null : principal.memberId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
