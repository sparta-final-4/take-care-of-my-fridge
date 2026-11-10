package org.example.takecareofmyfridgebe.global.config.cache;

/**
 * Spring Cache 이름 모음. 이름이 Redis 키 접두어가 되므로 {도메인}:{목적} 형식으로 짓는다.
 * 등록하지 않은 이름도 기본 TTL로 캐시가 만들어지므로, 캐시 이름은 이 상수로만 쓴다.
 */
public final class CacheNames {

    public static final String RECIPE_CLASSIFICATIONS = "recipe:classifications";
    // 인기 레시피 순위만 (레시피 정보는 담지 않음). 예전 "recipe:popular"(응답 전체)와 이름을 달리해 예전 값을 읽지 않는다
    public static final String POPULAR_RECIPE_RANKING = "recipe:popular-ranking";
    public static final String POPULAR_SALES = "sale:popular";

    private CacheNames() {
    }
}
