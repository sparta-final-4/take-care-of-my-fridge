package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeSearchResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 레시피 키워드 검색 (ES). 조건에 맞는 레시피 ID만 고르고, 목록 응답은 recipe 도메인이 DB에서 만든다.
 * <br>
 * 키워드 검색이 0건이면 벡터 검색(kNN)으로 한 번 더 찾는다. 결과가 1건이라도 있으면 임베딩을 호출하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeSearchService {

    // 이름이 가장 중요하고, 재료가 그다음이다
    private static final List<String> KEYWORD_FIELDS = List.of("name^3", "ingredients.text^2", "summary");

    private final ElasticsearchClient esClient;
    private final RecipeSearchProperties properties;
    private final RecipeEmbedder embedder;

    public RecipeSearchResult search(RecipeListRequest request) throws IOException {
        RecipeSearchResult keywordResult = toResult(esClient.search(keywordRequest(request), Void.class));
        if (keywordResult.total() > 0 || !properties.useVector()) {
            return keywordResult;
        }
        return vectorSearch(request);
    }

    /**
     * 단어가 맞지 않아 0건인 검색어("칼칼한 국물")를 의미가 가까운 레시피로 다시 찾는다.
     * <br>
     * 벡터 쪽 실패(OpenAI, ES)는 밖으로 내보내지 않고 0건으로 돌려준다. 키워드 결과도 0건이었으므로 사용자가 보는 결과는 같다.
     */
    private RecipeSearchResult vectorSearch(RecipeListRequest request) {
        long started = System.nanoTime();
        try {
            float[] vector = embedder.embed(request.keyword());
            long embedMs = (System.nanoTime() - started) / 1_000_000;

            RecipeSearchResult result = toResult(esClient.search(vectorRequest(request, vector), Void.class));
            // 기준선(min-similarity)과 k를 정하는 근거로 쓴다
            log.info("레시피 벡터 폴백: keyword={}, bm25Total=0, vectorTotal={}, embedMs={}",
                    request.keyword(), result.total(), embedMs);
            return result;
        } catch (IOException | RuntimeException e) {
            log.warn("레시피 벡터 폴백 실패, 0건으로 응답: keyword={}", request.keyword(), e);
            return new RecipeSearchResult(List.of(), 0);
        }
    }

    private static RecipeSearchResult toResult(SearchResponse<Void> response) {
        List<Long> recipeIds = response.hits().hits().stream()
                .map(hit -> Long.valueOf(hit.id()))
                .toList();
        long total = response.hits().total() == null ? recipeIds.size() : response.hits().total().value();
        return new RecipeSearchResult(recipeIds, total);
    }

    /**
     * 검색어는 점수를 매기는 조건(must), 분류·최댓값은 점수와 상관없는 조건(filter)으로 건다.
     * <br>
     * 문서 본문은 받지 않고 ID와 전체 건수만 받는다.
     */
    SearchRequest keywordRequest(RecipeListRequest request) {
        return SearchRequest.of(s -> s
                .index(properties.indexProperties().recipe().alias())
                .query(q -> q.bool(b -> b
                        .must(keywordQuery(request.keyword()))
                        .filter(filters(request))))
                .sort(sortOptions(request.sort()))
                .from((request.page() - 1) * request.size())
                .size(request.size())
                .source(src -> src.fetch(false))
                .trackTotalHits(t -> t.enabled(true)));
    }

    /**
     * 키워드 검색과 같은 filter, 정렬, 페이지를 쓰고 검색어 조건만 kNN으로 바꾼다.
     * <br>
     * 유사도가 min-similarity(코사인) 미만인 레시피는 빼고, 가까운 k건 안에서 요청한 정렬로 보여준다.
     * 그래서 전체 건수는 최대 k건이다.
     */
    SearchRequest vectorRequest(RecipeListRequest request, float[] vector) {
        RecipeSearchProperties.VectorProperties vectorProperties = properties.vector();
        return SearchRequest.of(s -> s
                .index(properties.indexProperties().recipe().alias())
                .knn(k -> k
                        .field("embedding")
                        .queryVector(toList(vector))
                        .k(vectorProperties.k())
                        .numCandidates(vectorProperties.numCandidates())
                        .similarity(vectorProperties.minSimilarity())
                        .filter(filters(request)))
                .sort(sortOptions(request.sort()))
                .from((request.page() - 1) * request.size())
                .size(request.size())
                .source(src -> src.fetch(false))
                .trackTotalHits(t -> t.enabled(true)));
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    /**
     * 검색어의 단어가 이름, 재료, 요약 중 어딘가에 모두 있어야 한다 (cross_fields + AND).
     * <br>
     * 정렬은 기존 목록 API의 정렬 조건을 그대로 따르므로, 넓게 걸리지 않도록 OR가 아니라 AND로 찾는다.
     */
    private static Query keywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields(KEYWORD_FIELDS)
                .type(TextQueryType.CrossFields)
                .operator(Operator.And)));
    }

    // 값이 있는 조건만 건다. 조리 시간이 비어 있는 레시피는 MySQL 검색과 같이 조건을 걸면 제외된다
    private static List<Query> filters(RecipeListRequest request) {
        List<Query> filters = new ArrayList<>();
        if (request.cuisineType() != null) {
            filters.add(term("cuisine_type", request.cuisineType()));
        }
        if (request.category() != null) {
            filters.add(term("category", request.category()));
        }
        if (request.maxCookTimeMin() != null) {
            filters.add(atMost("cook_time_min", request.maxCookTimeMin()));
        }
        if (request.maxDifficulty() != null) {
            filters.add(atMost("difficulty", request.maxDifficulty()));
        }
        return filters;
    }

    private static Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    // field <= max
    private static Query atMost(String field, int max) {
        return Query.of(q -> q.range(r -> r.number(n -> n.field(field).lte((double) max))));
    }

    // MySQL 검색(RecipeRepositoryImpl)과 같은 정렬. 같은 값끼리는 최신 레시피(id 내림차순)가 먼저 온다
    private List<SortOptions> sortOptions(RecipeSort sort) {
        return switch (sort) {
            case LATEST -> List.of(sortBy("id", SortOrder.Desc));
            case NAME_ASC -> List.of(sortBy("name.keyword", SortOrder.Asc), sortBy("id", SortOrder.Desc));
            case DIFFICULTY_ASC -> List.of(sortBy("difficulty", SortOrder.Asc), sortBy("id", SortOrder.Desc));
        };
    }

    private static SortOptions sortBy(String field, SortOrder order) {
        return SortOptions.of(o -> o.field(f -> f.field(field).order(order)));
    }

}
