package org.example.takecareofmyfridgebe.domain.search.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeSearchFacade {

    private final RecipeService recipeService;
    private final RecipeSearchService recipeSearchService;
    private final RecipeSearchProperties searchProperties;

    /**
     * 레시피 검색
     */
    public PageResponse<RecipeListItemResponse> getRecipes(RecipeListRequest request) {
        if (request.keyword() == null || !searchProperties.useElasticSearch()) {
            // mysql 조회
            return recipeService.getRecipes(request);
        }

        RecipeSearchResult result;
        try {
            // es 조회
            result = recipeSearchService.search(request);
        } catch (IOException | RuntimeException e) {
            // es 조회 실패 시 mysql 조회로 대체한다
            log.warn("ES 레시피 검색 실패, MySQL 검색으로 대체: keyword={}", request.keyword(), e);
            return recipeService.getRecipes(request);
        }

        return recipeService.getRecipesByIds(result.recipeIds(), result.total(), request);
    }

}
