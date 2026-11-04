package org.example.takecareofmyfridgebe.domain.search.dto;

import java.util.List;

/**
 * @param vectorFallback 키워드 검색이 0건이라 벡터 검색(kNN)으로 찾은 결과인지
 */
public record RecipeSearchResult(
        List<Long> recipeIds,
        long total,
        boolean vectorFallback
) {

    public RecipeSearchResult(List<Long> recipeIds, long total) {
        this(recipeIds, total, false);
    }

    public RecipeSearchResult asVectorFallback() {
        return new RecipeSearchResult(recipeIds, total, true);
    }
}
