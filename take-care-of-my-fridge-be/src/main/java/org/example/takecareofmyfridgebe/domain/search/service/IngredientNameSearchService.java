package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.termvectors.Term;
import co.elastic.clients.elasticsearch.core.termvectors.TermVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.dto.IngredientCandidate;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 냉장고 재료명 후보 (ES). 재료명 하나를 받아 표준 재료 후보를 점수(0~1) 높은 순으로 돌려준다.
 * <br>
 * 1) 별칭: 입력 전체 키가 aliases 키와 같으면 1.0 ("계란" -> 달걀)
 * 2) nori BM25: name.ko, aliases.ko에서 찾고, 입력과 같은 이름의 재료가 있었다면 받았을 점수로 나눠 0~1로 맞춘다.
 *    BM25 점수는 쿼리마다 범위가 달라 그대로는 비교할 수 없으므로 이렇게 정규화한다
 * <br>
 * 후보마다 두 점수 중 높은 쪽을 쓴다. 점수만으로 자동 확정하면 오답(콜라 -> 루콜라 0.8대)이 많으므로 사용자가 확인하는 용도로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientNameSearchService {

    private static final int MAX_CANDIDATES = 5;
    private static final String SEARCH_ANALYZER = "korean_search";

    // ES BM25 기본 k1. b와 가중치는 ingredients_index.json의 similarity, 검색 쿼리와 같아야 한다
    private static final double K1 = 1.2;
    // aliases.ko는 별칭이 많은 재료가 길이 보정으로 깎이지 않도록 b=0 (alias_bm25)
    private static final List<TextField> TEXT_FIELDS = List.of(
            new TextField("name.ko", 2.0, 0.75),
            new TextField("aliases.ko", 1.0, 0.0));

    private final ElasticsearchClient esClient;
    private final RecipeSearchProperties properties;

    /**
     * @param name 다듬은 재료명 하나 (IngredientMatcher.normalize 결과)
     * @return 점수(0~1) 높은 순 후보. 검색 엔진을 쓰지 않거나 장애면 빈 목록
     */
    public List<IngredientCandidate> findCandidates(String name) {
        if (!properties.useElasticSearch() || name == null || name.isBlank()) {
            return List.of();
        }

        try {
            Map<String, Double> scoreByName = aliasScores(name);
            bm25Scores(name).forEach((value, score) -> scoreByName.merge(value, score, Math::max));
            return rank(scoreByName);
        } catch (IOException | RuntimeException e) {
            // 후보는 사용자가 확인하는 보조 정보라 ES 장애로 요청을 실패시키지 않는다
            log.warn("재료 후보 조회 실패, 빈 목록 반환: name={}", name, e);
            return List.of();
        }
    }

    // 입력 전체 키가 별칭과 같은 재료는 1.0
    private Map<String, Double> aliasScores(String name) throws IOException {
        Map<String, Double> scores = new HashMap<>();
        for (IngredientHit hit : search(aliasRequest(IngredientAliasDictionary.key(name)))) {
            scores.put(hit.name(), 1.0);
        }
        return scores;
    }

    // nori BM25 점수를 완벽 일치 점수로 나눠 0~1로 맞춘다
    private Map<String, Double> bm25Scores(String name) throws IOException {
        double perfect = perfectScore(termVectors(name));
        if (perfect <= 0) {
            return Map.of();
        }

        Map<String, Double> scores = new HashMap<>();
        for (Hit<IngredientHit> hit : esClient.search(textRequest(name), IngredientHit.class).hits().hits()) {
            if (hit.source() != null && hit.score() != null) {
                scores.merge(hit.source().name(), Math.min(hit.score() / perfect, 1.0), Math::max);
            }
        }
        return scores;
    }

    private List<IngredientHit> search(SearchRequest request) throws IOException {
        return esClient.search(request, IngredientHit.class).hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    static List<IngredientCandidate> rank(Map<String, Double> scoreByName) {
        return scoreByName.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_CANDIDATES)
                .map(entry -> new IngredientCandidate(entry.getKey(), entry.getValue()))
                .toList();
    }

    // aliases 키가 입력 전체 키와 같은 재료. 점수와 상관없으므로 filter로 건다
    SearchRequest aliasRequest(String key) {
        return SearchRequest.of(s -> s
                .index(ingredientAlias())
                .query(q -> q.bool(b -> b.filter(f -> f.term(t -> t.field("aliases").value(key)))))
                .size(MAX_CANDIDATES)
                .source(src -> src.filter(f -> f.includes("name"))));
    }

    SearchRequest textRequest(String name) {
        List<String> fields = TEXT_FIELDS.stream().map(field -> field.path() + "^" + field.boost()).toList();
        return SearchRequest.of(s -> s
                .index(ingredientAlias())
                .query(q -> q.multiMatch(m -> m.query(name).fields(fields)))
                .size(MAX_CANDIDATES)
                .source(src -> src.filter(f -> f.includes("name"))));
    }

    // 입력을 검색 분석기로 나눈 토큰과 토큰별 문서 빈도, 필드 평균 길이. 입력을 가상 문서로 넘겨 색인 없이 얻는다
    private Map<String, TermVector> termVectors(String name) throws IOException {
        List<String> paths = TEXT_FIELDS.stream().map(TextField::path).toList();
        Map<String, Object> doc = Map.of("name", name, "aliases", List.of(name));
        return esClient.<Map<String, Object>>termvectors(t -> t
                .index(ingredientAlias())
                .doc(doc)
                .fields(paths)
                .perFieldAnalyzer(paths.stream().collect(Collectors.toMap(Function.identity(), path -> SEARCH_ANALYZER)))
                .termStatistics(true)
                .fieldStatistics(true)
                .positions(false)
                .offsets(false)).termVectors();
    }

    /**
     * 입력과 같은 이름의 재료 문서가 있었다면 받았을 BM25 점수. multi_match(best_fields)처럼 필드 중 최대를 쓴다.
     * <br>
     * 인덱스에 없는 토큰은 그 가상 문서 하나에만 있다고 본다(문서 빈도 1).
     */
    static double perfectScore(Map<String, TermVector> termVectors) {
        double best = 0;
        for (TextField field : TEXT_FIELDS) {
            TermVector vector = termVectors.get(field.path());
            if (vector == null || vector.fieldStatistics() == null || vector.fieldStatistics().docCount() == 0) {
                continue;
            }
            int docCount = vector.fieldStatistics().docCount();
            double avgLength = (double) vector.fieldStatistics().sumTtf() / docCount;
            int length = vector.terms().values().stream().mapToInt(Term::termFreq).sum();

            double score = 0;
            for (Term term : vector.terms().values()) {
                int docFreq = term.docFreq() == null ? 1 : Math.max(term.docFreq(), 1);
                // 쿼리에 같은 토큰이 n번 나오면 절도 n개라 점수도 n배
                score += term.termFreq() * bm25(term.termFreq(), docFreq, docCount, length, avgLength, field.boost(), field.b());
            }
            best = Math.max(best, score);
        }
        return best;
    }

    // Lucene BM25: boost * (k1 + 1) * idf * tf / (tf + k1 * (1 - b + b * dl / avgdl)). ES _explain의 boost 2.2가 (k1 + 1)이다
    static double bm25(int termFreq, int docFreq, int docCount, double length, double avgLength, double boost, double b) {
        double idf = Math.log(1 + (docCount - docFreq + 0.5) / (docFreq + 0.5));
        return boost * (K1 + 1) * idf * termFreq / (termFreq + K1 * (1 - b + b * length / avgLength));
    }

    private String ingredientAlias() {
        return properties.indexProperties().ingredient().alias();
    }

    // ingredients 인덱스 문서 중 조회에 필요한 필드
    record IngredientHit(String name) {}

    private record TextField(String path, double boost, double b) {}

}
