package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
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
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.VectorProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeSearchServiceTest {

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);
    private final RecipeEmbedder embedder = mock(RecipeEmbedder.class);
    private final RecipeSearchService service = service(true);

    private RecipeSearchService service(boolean vectorEnabled) {
        RecipeSearchProperties properties = new RecipeSearchProperties(
                SearchEngine.ELASTICSEARCH,
                new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500, false), null),
                new VectorProperties(vectorEnabled, 3, 20, 100, 0.5f, 100));
        return new RecipeSearchService(esClient, properties, embedder);
    }

    private static SearchResponse<Void> response(long total, String... ids) {
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(total).relation(TotalHitsRelation.Eq))
                        .hits(Arrays.stream(ids)
                                .map(id -> Hit.<Void>of(hit -> hit.index("recipes_1").id(id)))
                                .toList())));
    }

    @Test
    @DisplayName("검색어는 이름·재료·요약에 걸쳐 모든 단어가 있어야 하는 조건으로 찾는다")
    void keywordMatchesAllWordsAcrossFields() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "김치찌개", null, null, null, null, null, null);

        // When
        SearchRequest search = service.keywordRequest(request);

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
        List<Query> filters = service.keywordRequest(request).query().bool().filter();

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
        SearchRequest search = service.keywordRequest(request);

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
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(42, "5", "2"));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "계란", null, null, null, null, null, null));

        // Then
        assertEquals(List.of(5L, 2L), result.recipeIds());
        assertEquals(42, result.total());
    }

    @Test
    @DisplayName("키워드 검색이 1건 이상이면 임베딩을 호출하지 않는다")
    void keywordHitsSkipVector() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(1, "7"));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "두부조림", null, null, null, null, null, null));

        // Then
        assertEquals(List.of(7L), result.recipeIds());
        verify(embedder, never()).embed(anyString());
        verify(esClient, times(1)).search(any(SearchRequest.class), eq(Void.class));
    }

    @Test
    @DisplayName("키워드 검색이 0건이면 검색어를 임베딩해 벡터 검색 결과를 돌려준다")
    void zeroKeywordHitsFallBackToVector() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class)))
                .thenReturn(response(0))
                .thenReturn(response(2, "3", "9"));
        when(embedder.embed("칼칼한 국물")).thenReturn(new float[]{1, 0, 0});

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertEquals(List.of(3L, 9L), result.recipeIds());
        assertEquals(2, result.total());
    }

    @Test
    @DisplayName("벡터 검색은 키워드 검색과 같은 filter, 정렬, 페이지를 쓰고 검색어 조건만 kNN으로 바꾼다")
    void vectorRequestKeepsFiltersSortAndPage() {
        // Given
        RecipeListRequest request = new RecipeListRequest(RecipeSort.NAME_ASC, "칼칼한 국물", "한식", null, 30, null, 2, 10);

        // When
        SearchRequest search = service.vectorRequest(request, new float[]{1, 0, 0});

        // Then
        KnnSearch knn = search.knn().get(0);
        assertEquals(List.of("recipes"), search.index());
        assertNull(search.query());
        assertEquals("embedding", knn.field());
        assertEquals(List.of(1f, 0f, 0f), knn.queryVector());
        assertEquals(20, knn.k());
        assertEquals(100, knn.numCandidates());
        assertEquals(0.5f, knn.similarity());
        assertEquals(2, knn.filter().size());
        assertEquals("cuisine_type", knn.filter().get(0).term().field());
        assertEquals("cook_time_min", knn.filter().get(1).range().number().field());
        assertEquals("name.keyword", search.sort().get(0).field().field());
        assertEquals(10, search.from());
        assertEquals(10, search.size());
    }

    @Test
    @DisplayName("임베딩이 실패하면 예외 없이 0건을 돌려준다")
    void vectorFailureReturnsEmpty() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(0));
        when(embedder.embed(anyString())).thenThrow(new IllegalStateException("OpenAI 장애"));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertEquals(List.of(), result.recipeIds());
        assertEquals(0, result.total());
    }

    @Test
    @DisplayName("벡터 검색을 끄면 키워드 검색이 0건이어도 벡터 검색을 하지 않는다")
    void vectorDisabledKeepsZero() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(0));

        // When
        RecipeSearchResult result = service(false).search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertEquals(0, result.total());
        verify(embedder, never()).embed(anyString());
        verify(esClient, times(1)).search(any(SearchRequest.class), eq(Void.class));
    }
}
