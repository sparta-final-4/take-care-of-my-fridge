package org.example.takecareofmyfridgebe.domain.search.config;

import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecipeSearchPropertiesTest extends MySqlContainerSupport {

    @Autowired
    private RecipeSearchProperties properties;

    @Test
    @DisplayName("application.yml의 검색 엔진 기본값은 elasticsearch다")
    void engine_isElasticsearch_byDefault() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        assertThat(sources.get(0).getProperty("app.search.engine")).isEqualTo("${SEARCH_ENGINE:elasticsearch}");
    }

    @Test
    @DisplayName("테스트 실행에서는 로컬 ES에 붙지 않도록 검색 엔진이 mysql로 고정된다")
    void engine_isMySql_whenTestProfile() {
        assertThat(properties.engine()).isEqualTo(SearchEngine.MYSQL);
        assertThat(properties.useMySQL()).isTrue();
        assertThat(properties.useElasticSearch()).isFalse();
    }

    @Test
    @DisplayName("레시피 인덱스 설정과 리소스 파일이 바인딩된다")
    void indexProperties_bindsRecipeIndex() {
        EsIndexProperties recipe = properties.indexProperties().recipe();

        assertThat(recipe.alias()).isEqualTo("recipes");
        assertThat(recipe.batchSize()).isEqualTo(500);
        assertThat(recipe.indexSettings().exists()).as("recipes_index.json").isTrue();
        assertThat(recipe.synonyms().exists()).as("ingredient_synonyms.json").isTrue();
        assertThat(recipe.extraSynonyms().exists()).as("synonyms.txt").isTrue();
        assertThat(properties.indexProperties().recipe().embeddingEnabled()).as("임베딩은 기본 켜짐").isTrue();
    }

    @Test
    @DisplayName("상품 인덱스 설정이 바인딩되고 레시피와 같은 동의어 파일을 쓴다")
    void indexProperties_bindsProductIndexWithSynonyms() {
        EsIndexProperties product = properties.indexProperties().product();

        assertThat(product.alias()).isEqualTo("products");
        assertThat(product.batchSize()).isEqualTo(500);
        assertThat(product.indexSettings().exists()).as("products_index.json").isTrue();
        assertThat(product.synonyms().getFilename()).isEqualTo("ingredient_synonyms.json");
        assertThat(product.extraSynonyms().getFilename()).isEqualTo("synonyms.txt");
    }

    @Test
    @DisplayName("재료 인덱스 설정과 별칭 사전 파일이 바인딩된다")
    void indexProperties_bindsIngredientIndexWithAliasDictionaries() {
        RecipeSearchProperties.IngredientIndexProperties ingredient = properties.indexProperties().ingredient();

        assertThat(ingredient.alias()).isEqualTo("ingredients");
        assertThat(ingredient.indexSettings().exists()).as("ingredients_index.json").isTrue();
        assertThat(ingredient.synonyms()).isNull();
        assertThat(ingredient.extraSynonyms()).isNull();
        assertThat(ingredient.aliasDictionaries().size()).isEqualTo(2);
        assertThat(ingredient.aliasDictionaries().stream().allMatch(resource -> resource.exists())).isTrue();
        assertThat(ingredient.embeddingEnabled()).isTrue();
    }

    @Test
    @DisplayName("벡터 검색 설정이 바인딩되고 기본값은 켜져 있다")
    void vector_bindsProperties() {
        RecipeSearchProperties.VectorProperties vector = properties.vector();

        assertThat(vector.enabled()).isTrue();
        assertThat(vector.dimensions()).isEqualTo(1536);
        assertThat(vector.k()).isEqualTo(10);
        assertThat(vector.numCandidates()).isEqualTo(100);
        assertThat(vector.minSimilarity()).isEqualTo(0.5f);
        assertThat(vector.batchSize()).isEqualTo(100);
        // 테스트는 MySQL 검색이라 벡터 검색을 켜도 쓰지 않는다
        assertThat(properties.useVector()).isFalse();
    }
}
