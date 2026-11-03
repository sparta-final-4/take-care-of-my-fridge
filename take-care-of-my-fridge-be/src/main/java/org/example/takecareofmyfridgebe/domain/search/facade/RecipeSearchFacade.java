package org.example.takecareofmyfridgebe.domain.search.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchTarget;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.SearchZeroResultService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeSearchFacade {

    private static final int SUGGESTION_SIZE = 8;

    private final RecipeService recipeService;
    private final RecipeSearchService recipeSearchService;
    private final RecipeSearchProperties searchProperties;
    private final SearchZeroResultService searchZeroResultService;

    /**
     * 레시피 검색
     */
    public PageResponse<RecipeListItemResponse> getRecipes(RecipeListRequest request) {
        PageResponse<RecipeListItemResponse> response = search(request);
        if (response.totalElements() == 0 && isPlainFirstPage(request)) {
            searchZeroResultService.record(SearchTarget.RECIPE, request.keyword());
        }
        return response;
    }

    private PageResponse<RecipeListItemResponse> search(RecipeListRequest request) {
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

    /**
     * 검색어 자동완성. ES가 실패하면 이름 앞부분이 같은 레시피만 MySQL에서 찾는다
     */
    public List<String> suggestNames(String keyword) {
        if (!searchProperties.useElasticSearch()) {
            return recipeService.suggestNames(keyword, SUGGESTION_SIZE);
        }
        try {
            return recipeSearchService.suggestNames(keyword, SUGGESTION_SIZE);
        } catch (IOException | RuntimeException e) {
            log.warn("ES 레시피 자동완성 실패, MySQL 조회로 대체: keyword={}", keyword, e);
            return recipeService.suggestNames(keyword, SUGGESTION_SIZE);
        }
    }

    // 필터 때문에 0건이 된 경우와 2페이지 이후는 사전 보강 근거가 아니라서 기록하지 않는다
    private boolean isPlainFirstPage(RecipeListRequest request) {
        return request.keyword() != null
                && request.page() == 1
                && request.cuisineType() == null
                && request.category() == null
                && request.maxCookTimeMin() == null
                && request.maxDifficulty() == null;
    }

}
