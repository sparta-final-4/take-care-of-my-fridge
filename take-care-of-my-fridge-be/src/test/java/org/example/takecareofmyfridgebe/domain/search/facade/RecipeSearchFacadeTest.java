package org.example.takecareofmyfridgebe.domain.search.facade;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeSearchFacadeTest {

    @Mock
    private RecipeService recipeService;
    @Mock
    private RecipeSearchService recipeSearchService;
    @Mock
    private RecipeSearchProperties searchProperties;
    @InjectMocks
    private RecipeSearchFacade facade;

    @SuppressWarnings("unchecked")
    private final PageResponse<RecipeListItemResponse> page = mock(PageResponse.class);

    private RecipeListRequest request(String keyword) {
        return new RecipeListRequest(null, keyword, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("검색 엔진이 MySQL이면 기존 LIKE 검색을 쓴다")
    void usesMySqlWhenEngineIsMySql() throws IOException {
        // Given
        RecipeListRequest request = request("김치");
        when(searchProperties.useElasticSearch()).thenReturn(false);
        when(recipeService.getRecipes(request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getRecipes(request));
        verify(recipeSearchService, never()).search(any());
    }

    @Test
    @DisplayName("검색어가 없으면 ES 엔진이어도 기존 목록 조회를 쓴다")
    void usesMySqlWithoutKeyword() throws IOException {
        // Given
        RecipeListRequest request = request(null);
        when(recipeService.getRecipes(request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getRecipes(request));
        verify(recipeSearchService, never()).search(any());
    }

    @Test
    @DisplayName("ES 엔진이고 검색어가 있으면 ES가 고른 ID 순서와 건수로 목록을 만든다")
    void usesElasticsearch() throws IOException {
        // Given
        RecipeListRequest request = request("계란");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(recipeSearchService.search(request)).thenReturn(new RecipeSearchResult(List.of(5L, 2L), 12));
        when(recipeService.getRecipesByIds(List.of(5L, 2L), 12, request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getRecipes(request));
        verify(recipeService, never()).getRecipes(any());
    }

    @Test
    @DisplayName("ES 검색이 실패하면 MySQL LIKE 검색으로 대체한다")
    void fallsBackToMySqlOnFailure() throws IOException {
        // Given
        RecipeListRequest request = request("계란");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(recipeSearchService.search(request)).thenThrow(new IOException("ES 연결 실패"));
        when(recipeService.getRecipes(request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getRecipes(request));
        verify(recipeService, never()).getRecipesByIds(anyList(), anyLong(), any());
    }
}
