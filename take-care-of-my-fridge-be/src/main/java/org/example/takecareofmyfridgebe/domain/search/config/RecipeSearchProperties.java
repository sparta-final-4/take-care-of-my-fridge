package org.example.takecareofmyfridgebe.domain.search.config;

import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "app.search")
public record RecipeSearchProperties(
        SearchEngine engine,
        IndexProperties indexProperties
) {

    public record IndexProperties(
            RecipeIndexProperties recipe,
            ProductIndexProperties product
    ) {}

    public record RecipeIndexProperties(
            String alias,
            Resource indexSettings,
            Resource synonyms,
            Resource extraSynonyms,
            int batchSize
    ) implements EsIndexProperties {}

    // 상품 인덱스는 동의어 필터가 없어 synonyms, extraSynonyms를 비워 둔다
    public record ProductIndexProperties(
            String alias,
            Resource indexSettings,
            Resource synonyms,
            Resource extraSynonyms,
            int batchSize
    ) implements EsIndexProperties {}

    public boolean useElasticSearch() {
        return engine == SearchEngine.ELASTICSEARCH;
    }

    public boolean useMySQL() {
        return engine == SearchEngine.MYSQL;
    }

}
