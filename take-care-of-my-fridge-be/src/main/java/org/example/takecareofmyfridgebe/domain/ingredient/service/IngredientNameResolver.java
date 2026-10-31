package org.example.takecareofmyfridgebe.domain.ingredient.service;

import java.util.Collection;
import java.util.Map;

/**
 * 사전에서 바로 찾지 못한 재료명을 표준 재료명(ingredients.name)으로 바꾼다.
 * <br>
 * 구현은 검색 엔진(ES 별칭 + 벡터)에 있고, IngredientMatcher는 이 인터페이스만 안다.
 */
public interface IngredientNameResolver {

    /**
     * @param names 다듬은 재료명 목록 (IngredientMatcher.normalize 결과)
     * @return 키: 입력 이름, 값: 표준 재료명. 못 찾은 이름은 넣지 않으며, 검색 엔진 장애 시 빈 맵을 돌려준다
     */
    Map<String, String> resolveAll(Collection<String> names);
}
