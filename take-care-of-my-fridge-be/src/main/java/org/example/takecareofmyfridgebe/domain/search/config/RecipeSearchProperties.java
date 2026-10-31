package org.example.takecareofmyfridgebe.domain.search.config;

import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "app.search")
public record RecipeSearchProperties(
        SearchEngine engine,
        IndexProperties indexProperties,
        VectorProperties vector
) {

    public record IndexProperties(
            RecipeIndexProperties recipe,
            ProductIndexProperties product
    ) {}

    /**
     * embeddingEnabled: 색인할 때 레시피 임베딩을 넣을지. 벡터 검색(vector.enabled)과 따로 켠다.
     * 임베딩을 다 채운 뒤에 검색을 켜야 하므로 둘을 나눈다.
     */
    public record RecipeIndexProperties(
            String alias,
            Resource indexSettings,
            Resource synonyms,
            Resource extraSynonyms,
            int batchSize,
            boolean embeddingEnabled
    ) implements EsIndexProperties {}

    // 상품 인덱스는 동의어 필터가 없어 synonyms, extraSynonyms를 비워 둔다
    public record ProductIndexProperties(
            String alias,
            Resource indexSettings,
            Resource synonyms,
            Resource extraSynonyms,
            int batchSize
    ) implements EsIndexProperties {}

    // 검색어가 글자로 맞지 않아 0건일 때 쓰는 벡터 검색 설정
    public record VectorProperties(
            boolean enabled,
            int dimensions,
            int k,
            int numCandidates,
            float minSimilarity,
            int batchSize
    ) {}

    public boolean useElasticSearch() {
        return engine == SearchEngine.ELASTICSEARCH;
    }

    public boolean useMySQL() {
        return engine == SearchEngine.MYSQL;
    }

    // 벡터 검색은 ES 검색 위에서만 동작한다
    public boolean useVector() {
        return useElasticSearch() && vector != null && vector.enabled();
    }

}
