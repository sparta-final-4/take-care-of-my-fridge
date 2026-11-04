package org.example.takecareofmyfridgebe.domain.recipe.dto;

/**
 * 후보 검색용 레시피 정보.
 *
 * @param cookTimeMin                 조리 시간(분). 출처(MFDS)에 따라 비어 있을 수 있다
 * @param nonBasicIngredientCount     기본 양념을 뺀 재료 종류 수
 */
public record RecipeCandidateInfo(
        Long recipeId,
        String name,
        String category,
        Integer cookTimeMin,
        int nonBasicIngredientCount
) {}
