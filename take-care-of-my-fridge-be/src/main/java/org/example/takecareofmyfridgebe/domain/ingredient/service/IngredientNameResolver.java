package org.example.takecareofmyfridgebe.domain.ingredient.service;

import org.example.takecareofmyfridgebe.domain.ingredient.dto.IngredientCandidate;

import java.util.List;

/**
 * 사전에서 바로 찾지 못한 재료명에 대한 표준 재료 후보를 찾는다.
 * <br>
 * 구현은 검색 엔진(ES 별칭 + nori BM25)에 있고, IngredientMatcher는 이 인터페이스만 안다.
 */
public interface IngredientNameResolver {

    /**
     * @param name 다듬은 재료명 하나 (IngredientMatcher.normalize 결과)
     * @return 점수(0~1) 높은 순 후보. 검색 엔진을 쓰지 않거나 장애면 빈 목록
     */
    List<IngredientCandidate> findCandidates(String name);
}
