package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.ProductIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);
    private final RecipeSearchProperties properties = new RecipeSearchProperties(
            SearchEngine.ELASTICSEARCH,
            new IndexProperties(null, new ProductIndexProperties("products", null, null, null, 500)));
    private final ProductSearchService service = new ProductSearchService(esClient, properties);

    @Test
    @DisplayName("검색어의 모든 단어가 상품명·설명 중 어딘가에 있어야 하고, products alias로 ID만 조회한다")
    void keywordMatchesAllWordsAcrossFields() {
        // Given
        ProductListRequest request = new ProductListRequest(null, "국산 대파", null, null, null, null, null);

        // When
        SearchRequest search = service.searchRequest(request);

        // Then
        assertEquals(List.of("products"), search.index());
        MultiMatchQuery keyword = search.query().bool().must().get(0).multiMatch();
        assertEquals("국산 대파", keyword.query());
        assertEquals(List.of("name^3", "description"), keyword.fields());
        assertEquals(TextQueryType.CrossFields, keyword.type());
        assertEquals(Operator.And, keyword.operator());
        assertEquals(false, search.source().fetch());
    }

    @Test
    @DisplayName("조건이 없어도 판매 중지 상품은 상태 filter로 제외한다")
    void excludesStoppedProducts() {
        // Given
        ProductListRequest request = new ProductListRequest(null, "대파", null, null, null, null, null);

        // When
        List<Query> filters = service.searchRequest(request).query().bool().filter();

        // Then
        assertEquals(1, filters.size());
        assertEquals("status", filters.get(0).terms().field());
        assertEquals(List.of("ON_SALE", "SOLD_OUT"),
                filters.get(0).terms().terms().value().stream().map(FieldValue::stringValue).toList());
    }

    @Test
    @DisplayName("종류와 가격 범위는 점수에 영향 없는 filter로 건다")
    void conditionsBecomeFilters() {
        // Given
        ProductListRequest request = new ProductListRequest(null, "대파", ProductType.INGREDIENT, 1000, null, null, null);

        // When
        List<Query> filters = service.searchRequest(request).query().bool().filter();

        // Then
        assertEquals(3, filters.size());
        assertEquals("product_type", filters.get(1).term().field());
        assertEquals("INGREDIENT", filters.get(1).term().value().stringValue());
        assertEquals("price", filters.get(2).range().number().field());
        assertEquals(1000.0, filters.get(2).range().number().gte());
        assertNull(filters.get(2).range().number().lte());
    }

    @Test
    @DisplayName("1부터 시작하는 페이지를 from/size로 바꾸고, 목록 API와 같은 정렬을 건다")
    void pageAndSort() {
        // Given
        ProductListRequest request = new ProductListRequest(ProductSort.PRICE_ASC, "대파", null, null, null, 2, 16);

        // When
        SearchRequest search = service.searchRequest(request);

        // Then
        assertEquals(16, search.from());
        assertEquals(16, search.size());
        assertEquals("price", search.sort().get(0).field().field());
        assertEquals(SortOrder.Asc, search.sort().get(0).field().order());
        assertEquals("id", search.sort().get(1).field().field());
        assertEquals(SortOrder.Desc, search.sort().get(1).field().order());
    }

    @Test
    @DisplayName("검색 결과의 문서 id를 순서대로 상품 ID로 바꾸고 전체 건수를 함께 돌려준다")
    void mapsHitsToProductIds() throws IOException {
        // Given
        SearchResponse<Void> response = SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(7).relation(TotalHitsRelation.Eq))
                        .hits(List.of(
                                Hit.of(hit -> hit.index("products_1").id("9")),
                                Hit.of(hit -> hit.index("products_1").id("4"))))));
        when(esClient.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response);

        // When
        ProductSearchResult result = service.search(new ProductListRequest(null, "대파", null, null, null, null, null));

        // Then
        assertEquals(List.of(9L, 4L), result.productIds());
        assertEquals(7, result.total());
    }
}
