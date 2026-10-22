package org.example.takecareofmyfridgebe.domain.recipe.dto;

// 정렬 조건은 RecipeRepositoryImpl이 QueryDSL 정렬로 바꾼다.
public enum RecipeSort {
    LATEST,
    NAME_ASC,
    DIFFICULTY_ASC
}
