package org.example.takecareofmyfridgebe.global.config;

/**
 * Spring Cache 이름 모음. 이름이 Redis 키 접두어가 되므로 {도메인}:{목적} 형식으로 짓는다.
 * 새 캐시는 여기에 이름을 두고 CacheConfig에 TTL과 값 타입을 등록해야 쓸 수 있다.
 */
public final class CacheNames {

    public static final String RECIPE_CLASSIFICATIONS = "recipe:classifications";

    private CacheNames() {
    }
}
