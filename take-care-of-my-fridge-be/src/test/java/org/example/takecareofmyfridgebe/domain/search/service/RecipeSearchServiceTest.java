package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeSearchServiceTest {

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);
    private final RecipeSearchProperties properties = new RecipeSearchProperties(
            SearchEngine.ELASTICSEARCH,
            new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500)));
    private final RecipeSearchService service = new RecipeSearchService(esClient, properties);

    @Test
    @DisplayName("검색어는 이름·재료·요약에 걸쳐 모든 단어가 있어야 하는 조건으로 찾는다")
    void keywordMatchesAllWordsAcrossFields() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "김치찌개", null, null, null, null, null, null);

        // When
        SearchRequest search = service.searchRequest(request);

        // Then
        assertEquals(List.of("recipes"), search.index());
        MultiMatchQuery keyword = search.query().bool().must().get(0).multiMatch();
        assertEquals("김치찌개", keyword.query());
        assertEquals(List.of("name^3", "ingredients.text^2", "summary"), keyword.fields());
        assertEquals(TextQueryType.CrossFields, keyword.type());
        assertEquals(Operator.And, keyword.operator());
        assertEquals(List.of(), search.query().bool().filter());
    }

    @Test
    @DisplayName("분류와 최댓값 조건은 점수에 영향 없는 filter로 건다")
    void conditionsBecomeFilters() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "찌개", "한식", "국", 30, 3, null, null);

        // When
        List<Query> filters = service.searchRequest(request).query().bool().filter();

        // Then
        assertEquals(4, filters.size());
        assertEquals("cuisine_type", filters.get(0).term().field());
        assertEquals("한식", filters.get(0).term().value().stringValue());
        assertEquals("category", filters.get(1).term().field());
        assertEquals("cook_time_min", filters.get(2).range().number().field());
        assertEquals(30.0, filters.get(2).range().number().lte());
        assertEquals("difficulty", filters.get(3).range().number().field());
        assertEquals(3.0, filters.get(3).range().number().lte());
    }

    @Test
    @DisplayName("1부터 시작하는 페이지를 from/size로 바꾸고, 목록 API와 같은 정렬을 건다")
    void pageAndSort() {
        // Given
        RecipeListRequest request = new RecipeListRequest(RecipeSort.NAME_ASC, "찌개", null, null, null, null, 3, 10);

        // When
        SearchRequest search = service.searchRequest(request);

        // Then
        assertEquals(20, search.from());
        assertEquals(10, search.size());
        assertEquals("name.keyword", search.sort().get(0).field().field());
        assertEquals(SortOrder.Asc, search.sort().get(0).field().order());
        assertEquals("id", search.sort().get(1).field().field());
        assertEquals(SortOrder.Desc, search.sort().get(1).field().order());
    }

    @Test
    @DisplayName("검색 결과의 문서 id를 순서대로 레시피 ID로 바꾸고 전체 건수를 함께 돌려준다")
    void mapsHitsToRecipeIds() throws IOException {
        // Given
        SearchResponse<Void> response = SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(42).relation(TotalHitsRelation.Eq))
                        .hits(List.of(
                                Hit.of(hit -> hit.index("recipes_1").id("5")),
                                Hit.of(hit -> hit.index("recipes_1").id("2"))))));
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response);

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "계란", null, null, null, null, null, null));

        // Then
        assertEquals(List.of(5L, 2L), result.recipeIds());
        assertEquals(42, result.total());
    }
}
