package org.example.takecareofmyfridgebe.domain.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "app.search")
public record RecipeSearchProperties(
        SearchEngine engine,
        IndexProperties indexProperties
) {

    public record IndexProperties(
            RecipeIndexProperties recipe
    ) {}

    public record RecipeIndexProperties(
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
