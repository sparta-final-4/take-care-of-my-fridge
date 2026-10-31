package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IngredientIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.VectorProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.service.IngredientNameSearchService.IngredientHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngredientNameSearchServiceTest {

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);
    private final RecipeEmbedder embedder = mock(RecipeEmbedder.class);

    private static RecipeSearchProperties properties(SearchEngine engine, boolean autoMatchByVector) {
        return new RecipeSearchProperties(
                engine,
                new IndexProperties(null, null, new IngredientIndexProperties(
                        "ingredients", null, null, null, 500, List.of(), true, autoMatchByVector, 0.85f)),
                new VectorProperties(true, 1536, 20, 100, 0.5f, 100));
    }

    private static SearchResponse<IngredientHit> response(List<IngredientHit> docs) {
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(docs.size()).relation(TotalHitsRelation.Eq))
                        .hits(docs.stream()
                                .map(doc -> Hit.<IngredientHit>of(hit -> hit.index("ingredients_1").id(doc.name()).source(doc)))
                                .toList())));
    }

    @Test
    @DisplayName("입력 전체 키가 별칭이면 그 재료로 정한다")
    void picksWholeKeyFirst() {
        Map<String, String> result = IngredientNameSearchService.pickByAlias(
                List.of("절인 배추", "계란"),
                Map.of("절인배추", "절인 배추", "계란", "달걀"));

        assertEquals(Map.of("절인 배추", "절인 배추", "계란", "달걀"), result);
    }

    @Test
    @DisplayName("전체 키가 없으면 단어 키가 가리키는 재료가 하나뿐일 때만 정한다")
    void picksByWordOnlyWhenUnambiguous() {
        Map<String, String> nameByKey = Map.of("신선란", "달걀", "흙대파", "대파", "두부", "두부", "김치", "배추김치");

        Map<String, String> result = IngredientNameSearchService.pickByAlias(
                List.of("무항생제 신선란", "국산 흙대파", "두부 김치", "엄마표 반찬"), nameByKey);

        assertEquals(Map.of("무항생제 신선란", "달걀", "국산 흙대파", "대파"), result);
    }

    @Test
    @DisplayName("조회 키는 입력 전체 키 + 두 단어 이상일 때의 단어별 키다")
    void candidateKeysIncludeWordsOnlyForMultiWordNames() {
        assertEquals(List.of("무항생제신선란", "무항생제", "신선란"),
                IngredientNameSearchService.candidateKeys("무항생제 신선란"));
        assertEquals(List.of("계란"), IngredientNameSearchService.candidateKeys("계란"));
        assertTrue(IngredientNameSearchService.candidateKeys(" ").isEmpty());
    }

    @Test
    @DisplayName("끝 단어: 입력이 2자 이상 키로 끝나면 가장 긴 키의 재료로 정한다")
    void picksBySuffix() {
        Map<String, String> nameByKey = Map.of(
                "우유", "우유", "참외", "참외", "토마토", "토마토", "대추", "대추", "계란", "달걀", "김", "김");

        Map<String, String> result = IngredientNameSearchService.pickBySuffix(
                List.of("서울우유", "꿀참외", "대추토마토", "국산 계란"), nameByKey);

        assertEquals(Map.of("서울우유", "우유", "꿀참외", "참외", "대추토마토", "토마토", "국산 계란", "달걀"), result);
    }

    @Test
    @DisplayName("끝 단어: 앞에만 재료명이 있거나, 한 글자 키뿐이거나, 같은 길이 후보가 둘이면 정하지 않는다")
    void skipsPrefixSingleCharAndTies() {
        Map<String, String> nameByKey = Map.of(
                "새우", "새우", "마늘", "마늘", "김", "김", "참치", "참치", "꽁치", "꽁치");

        Map<String, String> result = IngredientNameSearchService.pickBySuffix(
                List.of("새우깡", "마늘빵", "조미김", "참치꽁치", "새우"), nameByKey);

        // 새우깡·마늘빵: 끝이 재료명이 아님 / 조미김: 한 글자 키 / 새우: 입력 전체와 같은 키는 별칭 단계 몫
        assertEquals(Map.of("참치꽁치", "꽁치"), result);
    }

    @Test
    @DisplayName("ES 검색을 쓰지 않으면 조회 없이 빈 결과를 돌려준다")
    void returnsEmptyWhenEngineIsMySql() {
        IngredientNameSearchService service = new IngredientNameSearchService(
                esClient, properties(SearchEngine.MYSQL, true), embedder);

        assertTrue(service.resolveAll(List.of("계란")).isEmpty());
        verifyNoInteractions(esClient, embedder);
    }

    @Test
    @DisplayName("ES 조회가 실패하면 예외 없이 빈 결과를 돌려주고 벡터 조회도 하지 않는다")
    void returnsEmptyWhenEsFails() throws IOException {
        IngredientNameSearchService service = new IngredientNameSearchService(
                esClient, properties(SearchEngine.ELASTICSEARCH, true), embedder);
        when(esClient.search(any(SearchRequest.class), eq(IngredientHit.class)))
                .thenThrow(new IOException("ES down"));

        assertTrue(service.resolveAll(List.of("계란")).isEmpty());
        verify(embedder, never()).embedAll(anyList());
    }

    @Test
    @DisplayName("별칭으로 못 찾으면 끝 단어로 찾고, 자동 벡터 매칭이 꺼져 있으면 임베딩하지 않는다")
    void resolvesBySuffixWithoutVector() throws IOException {
        IngredientNameSearchService service = new IngredientNameSearchService(
                esClient, properties(SearchEngine.ELASTICSEARCH, false), embedder);
        // 별칭 조회(terms)는 0건, 키 표 조회(전체)는 우유 문서 하나
        when(esClient.search(any(SearchRequest.class), eq(IngredientHit.class)))
                .thenReturn(response(List.of()))
                .thenReturn(response(List.of(new IngredientHit("우유", List.of("우유", "흰우유")))));

        Map<String, String> result = service.resolveAll(List.of("서울우유", "콜라"));

        assertEquals(Map.of("서울우유", "우유"), result);
        verify(embedder, never()).embedAll(anyList());
    }

}
