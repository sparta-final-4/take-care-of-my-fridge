package org.example.takecareofmyfridgebe.domain.recipe.dto;

// 정렬 조건은 RecipeRepositoryImpl이 QueryDSL 정렬로 바꾼다.
public enum RecipeSort {
    LATEST,
    // 검색어가 있을 때 ES 점수 순. MySQL에는 점수가 없어 LATEST와 같다
    RELEVANCE,
    NAME_ASC,
    DIFFICULTY_ASC
}
