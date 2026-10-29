package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductSearchResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 키워드 검색 (ES). 조건에 맞는 상품 ID만 고르고, 목록 응답은 product 도메인이 DB에서 만든다.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    // 상품명이 가장 중요하고, 설명은 보조로 본다
    private static final List<String> KEYWORD_FIELDS = List.of("name^3", "description");

    // 판매 중지(STOPPED) 상품은 공개 목록과 같이 제외한다
    private static final List<FieldValue> PUBLIC_STATUSES = List.of(
            FieldValue.of(ProductStatus.ON_SALE.name()),
            FieldValue.of(ProductStatus.SOLD_OUT.name()));

    private final ElasticsearchClient esClient;
    private final RecipeSearchProperties properties;

    public ProductSearchResult search(ProductListRequest request) throws IOException {
        SearchResponse<Void> response = esClient.search(searchRequest(request), Void.class);

        List<Long> productIds = response.hits().hits().stream()
                .map(hit -> Long.valueOf(hit.id()))
                .toList();
        long total = response.hits().total() == null ? productIds.size() : response.hits().total().value();
        return new ProductSearchResult(productIds, total);
    }

    /**
     * 검색어는 점수를 매기는 조건(must), 상태·종류·가격은 점수와 상관없는 조건(filter)으로 건다.
     * <br>
     * 문서 본문은 받지 않고 ID와 전체 건수만 받는다.
     */
    SearchRequest searchRequest(ProductListRequest request) {
        return SearchRequest.of(s -> s
                .index(properties.indexProperties().product().alias())
                .query(q -> q.bool(b -> b
                        .must(keywordQuery(request.keyword()))
                        .filter(filters(request))))
                .sort(sortOptions(request.sort()))
                .from((request.page() - 1) * request.size())
                .size(request.size())
                .source(src -> src.fetch(false))
                .trackTotalHits(t -> t.enabled(true)));
    }

    // 레시피 검색과 같이 검색어의 단어가 이름, 설명 중 어딘가에 모두 있어야 한다 (cross_fields + AND)
    private static Query keywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields(KEYWORD_FIELDS)
                .type(TextQueryType.CrossFields)
                .operator(Operator.And)));
    }

    // 값이 있는 조건만 건다. 가격 범위는 MySQL 검색과 같이 양 끝을 포함한다
    private static List<Query> filters(ProductListRequest request) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.terms(t -> t.field("status").terms(v -> v.value(PUBLIC_STATUSES)))));
        if (request.type() != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("product_type").value(request.type().name()))));
        }
        if (request.minPrice() != null || request.maxPrice() != null) {
            filters.add(Query.of(q -> q.range(r -> r.number(n -> {
                n.field("price");
                if (request.minPrice() != null) {
                    n.gte((double) request.minPrice());
                }
                if (request.maxPrice() != null) {
                    n.lte((double) request.maxPrice());
                }
                return n;
            }))));
        }
        return filters;
    }

    // MySQL 검색(ProductRepositoryImpl)과 같은 정렬. 같은 값끼리는 최신 상품(id 내림차순)이 먼저 온다
    private List<SortOptions> sortOptions(ProductSort sort) {
        return switch (sort) {
            case LATEST -> List.of(sortBy("id", SortOrder.Desc));
            case PRICE_ASC -> List.of(sortBy("price", SortOrder.Asc), sortBy("id", SortOrder.Desc));
            case PRICE_DESC -> List.of(sortBy("price", SortOrder.Desc), sortBy("id", SortOrder.Desc));
        };
    }

    private static SortOptions sortBy(String field, SortOrder order) {
        return SortOptions.of(o -> o.field(f -> f.field(field).order(order)));
    }

}
