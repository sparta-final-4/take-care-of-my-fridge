package org.example.takecareofmyfridgebe.global.config;

/**
 * Spring Cache 이름 모음. 이름이 Redis 키 접두어가 되므로 {도메인}:{목적} 형식으로 짓는다.
 * 등록하지 않은 이름도 기본 TTL로 캐시가 만들어지므로, 캐시 이름은 이 상수로만 쓴다.
 */
public final class CacheNames {

    public static final String RECIPE_CLASSIFICATIONS = "recipe:classifications";
    public static final String POPULAR_RECIPES = "recipe:popular";

    private CacheNames() {
    }
}
