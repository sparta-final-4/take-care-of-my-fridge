package org.example.takecareofmyfridgebe.domain.search.config;

import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.util.List;

@ConfigurationProperties(prefix = "app.search")
public record RecipeSearchProperties(
        SearchEngine engine,
        IndexProperties indexProperties,
        VectorProperties vector
) {

    public record IndexProperties(
            RecipeIndexProperties recipe,
            ProductIndexProperties product,
            IngredientIndexProperties ingredient
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

    /**
     * 냉장고 재료명 정제용 인덱스. 분석기 동의어 필터 대신 별칭을 문서 필드에 넣으므로 synonyms, extraSynonyms를 비워 둔다.
     * <br>
     * aliasDictionaries: {"그룹": {"별칭": "표준명"}} 형식 파일 목록. 뒤 파일이 같은 별칭을 덮어쓴다.
     * embeddingEnabled: 색인할 때 재료 임베딩을 넣을지. 레시피 벡터 검색(vector.enabled)과 따로 켠다.
     * autoMatchByVector: 자동 매칭에 벡터를 쓸지. 틀리면 냉장고에 그대로 저장되므로 측정 근거가 있을 때만 켠다.
     * minSimilarity: 벡터로 찾을 때 이 코사인 유사도 미만이면 버린다.
     */
    public record IngredientIndexProperties(
            String alias,
            Resource indexSettings,
            Resource synonyms,
            Resource extraSynonyms,
            int batchSize,
            List<Resource> aliasDictionaries,
            boolean embeddingEnabled,
            boolean autoMatchByVector,
            float minSimilarity
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
