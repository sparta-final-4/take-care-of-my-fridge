package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.TermvectorsResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.core.termvectors.Term;
import co.elastic.clients.elasticsearch.core.termvectors.TermVector;
import org.example.takecareofmyfridgebe.domain.ingredient.dto.IngredientCandidate;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngredientNameSearchServiceTest {

    private static final double DELTA = 0.001;

    private final ElasticsearchClient esClient = mock(ElasticsearchClient.class);

    private static RecipeSearchProperties properties(SearchEngine engine) {
        return new RecipeSearchProperties(
                engine,
                new IndexProperties(null, null, new IngredientIndexProperties(
                        "ingredients", null, null, null, 500, List.of(), true)),
                new VectorProperties(true, 1536, 20, 100, 0.5f, 100));
    }

    private static SearchResponse<IngredientHit> response(Map<String, Double> scoreByName) {
        return SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .total(t -> t.value(scoreByName.size()).relation(TotalHitsRelation.Eq))
                        .hits(scoreByName.entrySet().stream()
                                .map(entry -> Hit.<IngredientHit>of(hit -> hit
                                        .index("ingredients_1")
                                        .id(entry.getKey())
                                        .score(entry.getValue())
                                        .source(new IngredientHit(entry.getKey()))))
                                .toList())));
    }

    private static TermVector termVector(int docCount, long sumTtf, Map<String, Term> terms) {
        return TermVector.of(v -> v
                .fieldStatistics(f -> f.docCount(docCount).sumDocFreq(sumTtf).sumTtf(sumTtf))
                .terms(terms));
    }

    private static Term term(int termFreq, Integer docFreq) {
        return Term.of(t -> t.termFreq(termFreq).docFreq(docFreq));
    }

    @Test
    @DisplayName("BM25 계산은 ES _explain 결과와 같다 (소주: name.ko 17.3778)")
    void bm25MatchesEsExplain() {
        assertEquals(17.3778, IngredientNameSearchService.bm25(1, 1, 1085, 1, 2.4507, 2.0, 0.75), DELTA);
        // b=0이면 문서 길이와 상관없다
        assertEquals(IngredientNameSearchService.bm25(1, 5, 1085, 1, 4.4, 1.0, 0.0),
                IngredientNameSearchService.bm25(1, 5, 1085, 30, 4.4, 1.0, 0.0), DELTA);
    }

    @Test
    @DisplayName("완벽 일치 점수는 필드 중 최대이고, 인덱스에 없는 토큰은 문서 빈도 1로 본다")
    void perfectScoreUsesBestFieldAndTreatsUnknownTokenAsDocFreqOne() {
        Map<String, TermVector> vectors = Map.of(
                "name.ko", termVector(1085, 2659, Map.of("소주", term(1, 1))),
                "aliases.ko", termVector(1085, 4795, Map.of("소주", term(1, null))));

        double expected = IngredientNameSearchService.bm25(1, 1, 1085, 1, 2659.0 / 1085, 2.0, 0.75);
        assertEquals(expected, IngredientNameSearchService.perfectScore(vectors), DELTA);
        assertEquals(0.0, IngredientNameSearchService.perfectScore(Map.of()), DELTA);
    }

    @Test
    @DisplayName("후보는 점수 높은 순(같으면 이름 순)으로 최대 5개다")
    void ranksByScoreThenNameUpToFive() {
        List<IngredientCandidate> result = IngredientNameSearchService.rank(Map.of(
                "우유", 0.57, "달걀", 1.0, "두유", 0.3, "아몬드우유", 0.47, "가지", 0.3, "꿀", 0.1));

        assertEquals(List.of(
                new IngredientCandidate("달걀", 1.0),
                new IngredientCandidate("우유", 0.57),
                new IngredientCandidate("아몬드우유", 0.47),
                new IngredientCandidate("가지", 0.3),
                new IngredientCandidate("두유", 0.3)), result);
    }

    @Test
    @DisplayName("ES 검색을 쓰지 않거나 입력이 비면 조회 없이 빈 목록을 돌려준다")
    void returnsEmptyWhenEngineIsMySqlOrBlank() {
        assertTrue(new IngredientNameSearchService(esClient, properties(SearchEngine.MYSQL)).findCandidates("계란").isEmpty());
        assertTrue(new IngredientNameSearchService(esClient, properties(SearchEngine.ELASTICSEARCH)).findCandidates(" ").isEmpty());
        verifyNoInteractions(esClient);
    }

    @Test
    @DisplayName("ES 조회가 실패하면 예외 없이 빈 목록을 돌려준다")
    void returnsEmptyWhenEsFails() throws IOException {
        IngredientNameSearchService service = new IngredientNameSearchService(esClient, properties(SearchEngine.ELASTICSEARCH));
        when(esClient.search(any(SearchRequest.class), eq(IngredientHit.class))).thenThrow(new IOException("ES down"));

        assertTrue(service.findCandidates("계란").isEmpty());
    }

    @Test
    @DisplayName("별칭이 정확히 맞으면 1.0, 나머지는 BM25 점수를 완벽 일치 점수로 나눈 값이다")
    @SuppressWarnings("unchecked")
    void scoresAliasAsOneAndNormalizesBm25() throws IOException {
        IngredientNameSearchService service = new IngredientNameSearchService(esClient, properties(SearchEngine.ELASTICSEARCH));
        Map<String, TermVector> vectors = Map.of("name.ko", termVector(1085, 2659, Map.of("우유", term(1, 3))));
        double perfect = IngredientNameSearchService.perfectScore(vectors);
        // 별칭 조회 -> 달걀, BM25 조회 -> 달걀(낮은 점수), 우유(완벽 일치의 절반)
        when(esClient.search(any(SearchRequest.class), eq(IngredientHit.class)))
                .thenReturn(response(Map.of("달걀", 30.0)))
                .thenReturn(response(Map.of("달걀", perfect * 0.2, "우유", perfect * 0.5)));
        when(esClient.termvectors(any(Function.class)))
                .thenReturn(TermvectorsResponse.of(r -> r.index("ingredients_1").found(true).took(1).version(0).termVectors(vectors)));

        List<IngredientCandidate> result = service.findCandidates("계란");

        assertEquals(2, result.size());
        assertEquals(new IngredientCandidate("달걀", 1.0), result.get(0));
        assertEquals("우유", result.get(1).value());
        assertEquals(0.5, result.get(1).score(), DELTA);
    }

}
