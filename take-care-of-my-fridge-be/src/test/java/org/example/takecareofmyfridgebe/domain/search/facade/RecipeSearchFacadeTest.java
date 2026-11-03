package org.example.takecareofmyfridgebe.domain.search.facade;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchTarget;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.SearchZeroResultService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private SearchZeroResultService searchZeroResultService;
    @InjectMocks
    private RecipeSearchFacade facade;

    @SuppressWarnings("unchecked")
    private final PageResponse<RecipeListItemResponse> page = mock(PageResponse.class);

    private RecipeListRequest request(String keyword) {
        return new RecipeListRequest(null, keyword, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("검색 엔진이 MySQL이면 기존 LIKE 검색을 쓴다")
    void getRecipes_usesMySql_whenEngineIsMySql() throws IOException {
        // Given
        RecipeListRequest request = request("김치");
        when(searchProperties.useElasticSearch()).thenReturn(false);
        when(recipeService.getRecipes(request)).thenReturn(page);

        // When & Then
        assertThat(facade.getRecipes(request)).isSameAs(page);
        verify(recipeSearchService, never()).search(any());
    }

    @Test
    @DisplayName("검색어가 없으면 ES 엔진이어도 기존 목록 조회를 쓴다")
    void getRecipes_usesMySql_whenKeywordMissing() throws IOException {
        // Given
        RecipeListRequest request = request(null);
        when(recipeService.getRecipes(request)).thenReturn(page);

        // When & Then
        assertThat(facade.getRecipes(request)).isSameAs(page);
        verify(recipeSearchService, never()).search(any());
    }

    @Test
    @DisplayName("ES 엔진이고 검색어가 있으면 ES가 고른 ID 순서와 건수로 목록을 만든다")
    void getRecipes_usesElasticsearch_whenKeywordPresent() throws IOException {
        // Given
        RecipeListRequest request = request("계란");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(recipeSearchService.search(request)).thenReturn(new RecipeSearchResult(List.of(5L, 2L), 12));
        when(recipeService.getRecipesByIds(List.of(5L, 2L), 12, request)).thenReturn(page);

        // When & Then
        assertThat(facade.getRecipes(request)).isSameAs(page);
        verify(recipeService, never()).getRecipes(any());
    }

    @Test
    @DisplayName("ES 검색이 실패하면 MySQL LIKE 검색으로 대체한다")
    void getRecipes_fallsBackToMySql_whenEsFails() throws IOException {
        // Given
        RecipeListRequest request = request("계란");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(recipeSearchService.search(request)).thenThrow(new IOException("ES 연결 실패"));
        when(recipeService.getRecipes(request)).thenReturn(page);

        // When & Then
        assertThat(facade.getRecipes(request)).isSameAs(page);
        verify(recipeService, never()).getRecipesByIds(anyList(), anyLong(), any());
    }

    @Test
    @DisplayName("필터 없이 첫 페이지 결과가 0건이면 검색어를 0건 검색어로 기록한다")
    void getRecipes_recordsZeroResult_whenNoHits() {
        // Given
        RecipeListRequest request = request("김치");
        when(recipeService.getRecipes(request)).thenReturn(emptyPage(1));

        // When
        facade.getRecipes(request);

        // Then
        verify(searchZeroResultService).record(SearchTarget.RECIPE, "김치");
    }

    @Test
    @DisplayName("결과가 있으면 0건 검색어로 기록하지 않는다")
    void getRecipes_skipsZeroResult_whenHitsExist() {
        // Given
        RecipeListRequest request = request("김치");
        when(recipeService.getRecipes(request)).thenReturn(new PageResponse<>(List.of(), 1, 20, 3, 1, true));

        // When
        facade.getRecipes(request);

        // Then
        verifyNoInteractions(searchZeroResultService);
    }

    @Test
    @DisplayName("음식 종류 필터를 걸어 0건이면 기록하지 않는다")
    void getRecipes_skipsZeroResult_whenFiltered() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "김치", "한식", null, null, null, null, null);
        when(recipeService.getRecipes(request)).thenReturn(emptyPage(1));

        // When
        facade.getRecipes(request);

        // Then
        verifyNoInteractions(searchZeroResultService);
    }

    @Test
    @DisplayName("2페이지 이후 요청이 0건이면 기록하지 않는다")
    void getRecipes_skipsZeroResult_whenNotFirstPage() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "김치", null, null, null, null, 2, null);
        when(recipeService.getRecipes(request)).thenReturn(emptyPage(2));

        // When
        facade.getRecipes(request);

        // Then
        verifyNoInteractions(searchZeroResultService);
    }

    @Test
    @DisplayName("자동완성은 ES 엔진이면 ES에서 이름 후보를 찾는다")
    void suggestNames_usesElasticsearch_whenEngineIsEs() throws IOException {
        // Given
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(recipeSearchService.suggestNames("김치", 8)).thenReturn(List.of("김치찌개", "김치볶음밥"));

        // When & Then
        assertThat(facade.suggestNames("김치")).containsExactly("김치찌개", "김치볶음밥");
        verify(recipeService, never()).suggestNames(any(), anyInt());
    }

    @Test
    @DisplayName("자동완성 중 ES가 실패하면 MySQL 이름 조회로 대체한다")
    void suggestNames_fallsBackToMySql_whenEsFails() throws IOException {
        // Given
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(recipeSearchService.suggestNames("김치", 8)).thenThrow(new IOException("down"));
        when(recipeService.suggestNames("김치", 8)).thenReturn(List.of("김치찌개"));

        // When & Then
        assertThat(facade.suggestNames("김치")).containsExactly("김치찌개");
    }

    @Test
    @DisplayName("자동완성은 MySQL 엔진이면 ES를 부르지 않는다")
    void suggestNames_usesMySql_whenEngineIsMySql() throws IOException {
        // Given
        when(searchProperties.useElasticSearch()).thenReturn(false);
        when(recipeService.suggestNames("김치", 8)).thenReturn(List.of());

        // When
        facade.suggestNames("김치");

        // Then
        verify(recipeSearchService, never()).suggestNames(any(), anyInt());
    }

    private PageResponse<RecipeListItemResponse> emptyPage(int pageNumber) {
        return new PageResponse<>(List.of(), pageNumber, 20, 0, 0, true);
    }
}
