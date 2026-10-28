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
    }
}
