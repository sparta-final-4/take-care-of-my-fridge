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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RecipeSearchPropertiesTest extends MySqlContainerSupport {

    @Autowired
    private RecipeSearchProperties properties;

    @Test
    @DisplayName("application.yml의 검색 엔진 기본값은 elasticsearch다")
    void defaultEngineIsElasticsearch() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        assertEquals("${SEARCH_ENGINE:elasticsearch}", sources.get(0).getProperty("app.search.engine"));
    }

    @Test
    @DisplayName("테스트 실행에서는 로컬 ES에 붙지 않도록 검색 엔진이 mysql로 고정된다")
    void testRunUsesMySql() {
        assertEquals(SearchEngine.MYSQL, properties.engine());
        assertTrue(properties.useMySQL());
        assertFalse(properties.useElasticSearch());
    }

    @Test
    @DisplayName("레시피 인덱스 설정과 리소스 파일이 바인딩된다")
    void bindsRecipeIndexProperties() {
        EsIndexProperties recipe = properties.indexProperties().recipe();

        assertEquals("recipes", recipe.alias());
        assertEquals(500, recipe.batchSize());
        assertTrue(recipe.indexSettings().exists(), "recipes_index.json");
        assertTrue(recipe.synonyms().exists(), "ingredient_synonyms.json");
        assertTrue(recipe.extraSynonyms().exists(), "synonyms.txt");
        assertTrue(properties.indexProperties().recipe().embeddingEnabled(), "임베딩은 기본 켜짐");
    }

    @Test
    @DisplayName("상품 인덱스 설정이 바인딩되고 동의어 파일은 없다")
    void bindsProductIndexProperties() {
        EsIndexProperties product = properties.indexProperties().product();

        assertEquals("products", product.alias());
        assertEquals(500, product.batchSize());
        assertTrue(product.indexSettings().exists(), "products_index.json");
        assertNull(product.synonyms());
        assertNull(product.extraSynonyms());
    }

    @Test
    @DisplayName("재료 인덱스 설정과 별칭 사전 파일이 바인딩된다")
    void bindsIngredientIndexProperties() {
        RecipeSearchProperties.IngredientIndexProperties ingredient = properties.indexProperties().ingredient();

        assertEquals("ingredients", ingredient.alias());
        assertTrue(ingredient.indexSettings().exists(), "ingredients_index.json");
        assertNull(ingredient.synonyms());
        assertNull(ingredient.extraSynonyms());
        assertEquals(2, ingredient.aliasDictionaries().size());
        assertTrue(ingredient.aliasDictionaries().stream().allMatch(resource -> resource.exists()));
        assertTrue(ingredient.embeddingEnabled());
        assertFalse(ingredient.autoMatchByVector());
        assertEquals(0.85f, ingredient.minSimilarity());
    }

    @Test
    @DisplayName("벡터 검색 설정이 바인딩되고 기본값은 꺼져 있다")
    void bindsVectorProperties() {
        RecipeSearchProperties.VectorProperties vector = properties.vector();

        assertFalse(vector.enabled());
        assertEquals(1536, vector.dimensions());
        assertEquals(10, vector.k());
        assertEquals(100, vector.numCandidates());
        assertEquals(0.5f, vector.minSimilarity());
        assertEquals(100, vector.batchSize());
        assertFalse(properties.useVector());
    }
}
