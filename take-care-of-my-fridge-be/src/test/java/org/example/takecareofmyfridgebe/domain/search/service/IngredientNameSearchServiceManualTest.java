package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.example.takecareofmyfridgebe.domain.ingredient.dto.IngredientCandidate;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IngredientIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.VectorProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 로컬 ES(ingredients 인덱스)에 붙여 findCandidates 결과를 눈으로 확인하는 수동 테스트. CI에서는 돌지 않는다.
 * <br>
 * ES_MANUAL_TEST=true INGREDIENT_NAMES="서울우유,무항생제 신선란" ./gradlew test --tests '*IngredientNameSearchServiceManualTest'
 * <br>
 * 다른 인덱스(alias)로 보려면 ES_INGREDIENT_ALIAS를 준다.
 */
@EnabledIfEnvironmentVariable(named = "ES_MANUAL_TEST", matches = "true")
class IngredientNameSearchServiceManualTest {

    private static final List<String> DEFAULT_NAMES = List.of(
            "서울우유", "무항생제 신선란", "국산 흙대파", "꿀참외", "계란", "새우깡", "마늘빵", "두부 김치", "콜라");

    @Test
    void printFindCandidates() throws Exception {
        String url = System.getenv().getOrDefault("ELASTICSEARCH_URL", "http://localhost:9200");
        String alias = System.getenv().getOrDefault("ES_INGREDIENT_ALIAS", "ingredients");
        try (ElasticsearchClient esClient = ElasticsearchClient.of(b -> b.host(url))) {
            IngredientNameSearchService service = new IngredientNameSearchService(esClient, properties(alias));

            System.out.println("===== findCandidates (" + alias + ") =====");
            for (String name : names()) {
                List<IngredientCandidate> candidates = service.findCandidates(name);
                String line = candidates.isEmpty() ? "(없음)" : candidates.stream()
                        .map(candidate -> "%s(%.2f)".formatted(candidate.value(), candidate.score()))
                        .collect(Collectors.joining(", "));
                System.out.printf("%s -> %s%n", name, line);
            }
        }
    }

    private static List<String> names() {
        String env = System.getenv("INGREDIENT_NAMES");
        if (env == null || env.isBlank()) {
            return DEFAULT_NAMES;
        }
        return Arrays.stream(env.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static RecipeSearchProperties properties(String alias) {
        return new RecipeSearchProperties(
                SearchEngine.ELASTICSEARCH,
                new IndexProperties(null, null, new IngredientIndexProperties(
                        alias, null, null, null, 500, List.of(), true)),
                new VectorProperties(true, 1536, 20, 100, 0.5f, 100));
    }

}
