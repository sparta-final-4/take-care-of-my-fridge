package org.example.takecareofmyfridgebe.infra.elasticsearch.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.ProductIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// ES 요청 없이 확인할 수 있는 인덱스 생성 본문(indexBody)만 검증한다. 실제 색인은 ES가 있어야 해서 로컬에서 확인한다.
class EsIndexerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    // 주어진 설정만 돌려주는 테스트용 하위 클래스. indexBody는 리더를 쓰지 않는다
    private EsIndexer indexerOf(EsIndexProperties index) {
        return new EsIndexer(mock(ElasticsearchClient.class), new SynonymRuleBuilder(objectMapper)) {
            @Override
            protected EsIndexProperties indexProperties() {
                return index;
            }

            @Override
            protected EsDocumentReader documentReader() {
                return mock(EsDocumentReader.class);
            }
        };
    }

    private List<String> synonymsOf(ObjectNode body, String filterName) {
        List<String> synonyms = new ArrayList<>();
        for (JsonNode rule : body.at("/settings/analysis/filter/" + filterName + "/synonyms")) {
            synonyms.add(rule.asString());
        }
        return synonyms;
    }

    @Test
    @DisplayName("설정 파일의 동의어 필터에 동의어 규칙을 채워 넣고, 나머지 설정은 그대로 둔다")
    void indexBody_fillsSynonymFilter() throws IOException {
        // Given
        EsIndexProperties recipeIndex = new RecipeIndexProperties(
                "recipes",
                new ClassPathResource("elasticsearch/recipes/recipes_index.json"),
                new ClassPathResource("elasticsearch/recipes/ingredient_synonyms.json"),
                new ClassPathResource("elasticsearch/recipes/synonyms.txt"),
                500, false);

        // When
        ObjectNode body = indexerOf(recipeIndex).indexBody();

        // Then
        List<String> synonyms = synonymsOf(body, "recipe_synonyms");
        assertThat(synonyms).hasSizeGreaterThan(100);
        assertThat(synonyms).contains("파스타, 스파게티");
        assertThat(body.at("/mappings/dynamic").asString()).isEqualTo("strict");
    }

    @Test
    @DisplayName("상품 인덱스도 레시피와 같은 동의어 규칙을 검색 분석기의 동의어 필터에 넣는다")
    void indexBody_fillsProductSynonymFilter() throws IOException {
        // Given
        EsIndexProperties productIndex = new ProductIndexProperties(
                "products",
                new ClassPathResource("elasticsearch/products/products_index.json"),
                new ClassPathResource("elasticsearch/recipes/ingredient_synonyms.json"),
                new ClassPathResource("elasticsearch/recipes/synonyms.txt"),
                500);
        EsIndexProperties recipeIndex = new RecipeIndexProperties(
                "recipes",
                new ClassPathResource("elasticsearch/recipes/recipes_index.json"),
                new ClassPathResource("elasticsearch/recipes/ingredient_synonyms.json"),
                new ClassPathResource("elasticsearch/recipes/synonyms.txt"),
                500, false);

        // When
        ObjectNode body = indexerOf(productIndex).indexBody();

        // Then
        List<String> synonyms = synonymsOf(body, "product_synonyms");
        assertThat(synonyms).isEqualTo(synonymsOf(indexerOf(recipeIndex).indexBody(), "recipe_synonyms"));
        assertThat(synonyms).anyMatch(rule -> rule.startsWith("달걀, ") && rule.contains("계란"));
        assertThat(body.at("/settings/analysis/analyzer/korean_search/filter/3").asString()).isEqualTo("product_synonyms");
        // 색인 분석기에는 넣지 않는다 (검색할 때만 펼친다)
        assertThat(body.at("/settings/analysis/analyzer/korean/filter").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("필터 이름과 상관없이 synonym_graph 필터를 모두 찾아 채운다")
    void indexBody_fillsAnySynonymFilterName() throws IOException {
        // Given: 레시피가 아닌 다른 인덱스 설정
        String settings = """
                {"settings": {"analysis": {"filter": {
                  "product_synonyms": {"type": "synonym_graph", "synonyms": []},
                  "my_stop": {"type": "stop", "stopwords": ["의"]}
                }}}}
                """;
        EsIndexProperties productIndex = new RecipeIndexProperties(
                "products",
                new ByteArrayResource(settings.getBytes(StandardCharsets.UTF_8)),
                null,
                new ByteArrayResource("노트북, 랩탑".getBytes(StandardCharsets.UTF_8)),
                500, false);

        // When
        ObjectNode body = indexerOf(productIndex).indexBody();

        // Then
        assertThat(synonymsOf(body, "product_synonyms")).isEqualTo(List.of("노트북, 랩탑"));
        assertThat(body.at("/settings/analysis/filter/my_stop/stopwords/0").asString()).isEqualTo("의");
    }

    @Test
    @DisplayName("동의어 필터가 없는 설정은 그대로 반환한다")
    void indexBody_keepsSettings_whenNoSynonymFilter() throws IOException {
        // Given
        String settings = """
                {"settings": {"number_of_shards": 1}, "mappings": {"dynamic": "strict"}}
                """;
        EsIndexProperties plainIndex = new RecipeIndexProperties(
                "plain", new ByteArrayResource(settings.getBytes(StandardCharsets.UTF_8)), null, null, 500, false);

        // When
        ObjectNode body = indexerOf(plainIndex).indexBody();

        // Then
        assertThat(body).isEqualTo(objectMapper.readTree(settings));
    }
}
