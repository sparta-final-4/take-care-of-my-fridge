package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private final QueryEmbeddingService queryEmbedding = mock(QueryEmbeddingService.class);
    private final RecipeSearchService service = service(true);

    private RecipeSearchService service(boolean vectorEnabled) {
        RecipeSearchProperties properties = new RecipeSearchProperties(
                SearchEngine.ELASTICSEARCH,
                new IndexProperties(new RecipeIndexProperties("recipes", null, null, null, 500, false), null, null),
                new VectorProperties(vectorEnabled, 3, 20, 100, 0.5f, 100));
        return new RecipeSearchService(esClient, properties, queryEmbedding);
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
    void keywordRequest_matchesAllWordsAcrossFields() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "김치찌개", null, null, null, null, null, null);

        // When
        SearchRequest search = service.keywordRequest(request);

        // Then
        assertThat(search.index()).isEqualTo(List.of("recipes"));
        MultiMatchQuery keyword = search.query().bool().must().get(0).multiMatch();
        assertThat(keyword.query()).isEqualTo("김치찌개");
        assertThat(keyword.fields()).isEqualTo(List.of("name^3", "ingredients.text^2", "summary"));
        assertThat(keyword.type()).isEqualTo(TextQueryType.CrossFields);
        assertThat(keyword.operator()).isEqualTo(Operator.And);
        assertThat(search.query().bool().filter()).isEqualTo(List.of());
    }

    @Test
    @DisplayName("분류와 최댓값 조건은 점수에 영향 없는 filter로 건다")
    void keywordRequest_appliesConditionsAsFilters() {
        // Given
        RecipeListRequest request = new RecipeListRequest(null, "찌개", "한식", "국", 30, 3, null, null);

        // When
        List<Query> filters = service.keywordRequest(request).query().bool().filter();

        // Then
        assertThat(filters.size()).isEqualTo(4);
        assertThat(filters.get(0).term().field()).isEqualTo("cuisine_type");
        assertThat(filters.get(0).term().value().stringValue()).isEqualTo("한식");
        assertThat(filters.get(1).term().field()).isEqualTo("category");
        assertThat(filters.get(2).range().number().field()).isEqualTo("cook_time_min");
        assertThat(filters.get(2).range().number().lte()).isEqualTo(30.0);
        assertThat(filters.get(3).range().number().field()).isEqualTo("difficulty");
        assertThat(filters.get(3).range().number().lte()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("1부터 시작하는 페이지를 from/size로 바꾸고, 목록 API와 같은 정렬을 건다")
    void keywordRequest_appliesPageAndSort() {
        // Given
        RecipeListRequest request = new RecipeListRequest(RecipeSort.NAME_ASC, "찌개", null, null, null, null, 3, 10);

        // When
        SearchRequest search = service.keywordRequest(request);

        // Then
        assertThat(search.from()).isEqualTo(20);
        assertThat(search.size()).isEqualTo(10);
        assertThat(search.sort().get(0).field().field()).isEqualTo("name.keyword");
        assertThat(search.sort().get(0).field().order()).isEqualTo(SortOrder.Asc);
        assertThat(search.sort().get(1).field().field()).isEqualTo("id");
        assertThat(search.sort().get(1).field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    @DisplayName("관련도 순이면 점수 내림차순, 같은 점수끼리는 최신 레시피 순으로 정렬한다")
    void keywordRequest_sortsByScore_whenRelevance() {
        // Given
        RecipeListRequest request = new RecipeListRequest(RecipeSort.RELEVANCE, "김치", null, null, null, null, null, null);

        // When
        SearchRequest search = service.keywordRequest(request);

        // Then
        assertThat(search.sort().get(0).score().order()).isEqualTo(SortOrder.Desc);
        assertThat(search.sort().get(1).field().field()).isEqualTo("id");
        assertThat(search.sort().get(1).field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    @DisplayName("자동완성은 단어 앞글자로 모든 단어를 찾고, 검색어로 시작하는 이름을 올리며, 같은 이름은 한 번만 받는다")
    void suggestRequest_matchesNamePrefixes_andCollapsesSameName() {
        // When
        SearchRequest search = service.suggestRequest("돼지 김", 8);

        // Then
        assertThat(search.index()).isEqualTo(List.of("recipes"));
        MatchQuery words = search.query().bool().must().get(0).match();
        assertThat(words.field()).isEqualTo("name.autocomplete");
        assertThat(words.query().stringValue()).isEqualTo("돼지 김");
        assertThat(words.operator()).isEqualTo(Operator.And);
        assertThat(search.query().bool().should().get(0).prefix().field()).isEqualTo("name.keyword");
        assertThat(search.query().bool().should().get(0).prefix().value()).isEqualTo("돼지 김");
        assertThat(search.collapse().field()).isEqualTo("name.keyword");
        assertThat(search.sort().get(0).score().order()).isEqualTo(SortOrder.Desc);
        assertThat(search.sort().get(1).field().field()).isEqualTo("name.keyword");
        assertThat(search.size()).isEqualTo(8);
        assertThat(search.source().filter().includes()).isEqualTo(List.of("name"));
    }

    @Test
    @DisplayName("벡터 검색도 관련도 순이면 유사도 점수 내림차순으로 정렬한다")
    void vectorRequest_sortsByScore_whenRelevance() {
        // Given
        RecipeListRequest request = new RecipeListRequest(RecipeSort.RELEVANCE, "칼칼한 국물", null, null, null, null, null, null);

        // When
        SearchRequest search = service.vectorRequest(request, new float[]{1, 0, 0});

        // Then
        assertThat(search.sort().get(0).score().order()).isEqualTo(SortOrder.Desc);
        assertThat(search.sort().get(1).field().field()).isEqualTo("id");
    }

    @Test
    @DisplayName("검색 결과의 문서 id를 순서대로 레시피 ID로 바꾸고 전체 건수를 함께 돌려준다")
    void search_mapsHitsToRecipeIds() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(42, "5", "2"));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "계란", null, null, null, null, null, null));

        // Then
        assertThat(result.recipeIds()).isEqualTo(List.of(5L, 2L));
        assertThat(result.total()).isEqualTo(42);
    }

    @Test
    @DisplayName("키워드 검색이 1건 이상이면 임베딩을 호출하지 않는다")
    void search_skipsVector_whenKeywordHits() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(1, "7"));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "두부조림", null, null, null, null, null, null));

        // Then
        assertThat(result.recipeIds()).isEqualTo(List.of(7L));
        verify(queryEmbedding, never()).embed(anyString());
        verify(esClient, times(1)).search(any(SearchRequest.class), eq(Void.class));
    }

    @Test
    @DisplayName("키워드 검색이 0건이면 검색어를 임베딩해 벡터 검색 결과를 돌려준다")
    void search_fallsBackToVector_whenNoKeywordHits() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class)))
                .thenReturn(response(0))
                .thenReturn(response(2, "3", "9"));
        when(queryEmbedding.embed("칼칼한 국물")).thenReturn(Optional.of(new float[]{1, 0, 0}));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertThat(result.recipeIds()).isEqualTo(List.of(3L, 9L));
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("벡터 검색은 키워드 검색과 같은 filter, 정렬, 페이지를 쓰고 검색어 조건만 kNN으로 바꾼다")
    void vectorRequest_keepsFiltersSortAndPage() {
        // Given
        RecipeListRequest request = new RecipeListRequest(RecipeSort.NAME_ASC, "칼칼한 국물", "한식", null, 30, null, 2, 10);

        // When
        SearchRequest search = service.vectorRequest(request, new float[]{1, 0, 0});

        // Then
        KnnSearch knn = search.knn().get(0);
        assertThat(search.index()).isEqualTo(List.of("recipes"));
        assertThat(search.query()).isNull();
        assertThat(knn.field()).isEqualTo("embedding");
        assertThat(knn.queryVector()).isEqualTo(List.of(1f, 0f, 0f));
        assertThat(knn.k()).isEqualTo(20);
        assertThat(knn.numCandidates()).isEqualTo(100);
        assertThat(knn.similarity()).isEqualTo(0.5f);
        assertThat(knn.filter().size()).isEqualTo(2);
        assertThat(knn.filter().get(0).term().field()).isEqualTo("cuisine_type");
        assertThat(knn.filter().get(1).range().number().field()).isEqualTo("cook_time_min");
        assertThat(search.sort().get(0).field().field()).isEqualTo("name.keyword");
        assertThat(search.from()).isEqualTo(10);
        assertThat(search.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("임베딩이 실패하면 예외 없이 0건을 돌려준다")
    void search_returnsEmpty_whenVectorFails() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(0));
        when(queryEmbedding.embed(anyString())).thenThrow(new IllegalStateException("OpenAI 장애"));

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertThat(result.recipeIds()).isEqualTo(List.of());
        assertThat(result.total()).isEqualTo(0);
    }

    @Test
    @DisplayName("검색어 임베딩 호출 제한에 걸리면 벡터 검색 없이 0건을 돌려준다")
    void search_returnsEmpty_whenEmbeddingLimited() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(0));
        when(queryEmbedding.embed(anyString())).thenReturn(Optional.empty());

        // When
        RecipeSearchResult result = service.search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertThat(result.total()).isEqualTo(0);
        verify(esClient, times(1)).search(any(SearchRequest.class), eq(Void.class));
    }

    @Test
    @DisplayName("벡터 검색을 끄면 키워드 검색이 0건이어도 벡터 검색을 하지 않는다")
    void search_keepsZero_whenVectorDisabled() throws IOException {
        // Given
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(0));

        // When
        RecipeSearchResult result = service(false).search(new RecipeListRequest(null, "칼칼한 국물", null, null, null, null, null, null));

        // Then
        assertThat(result.total()).isEqualTo(0);
        verify(queryEmbedding, never()).embed(anyString());
        verify(esClient, times(1)).search(any(SearchRequest.class), eq(Void.class));
    }
}
