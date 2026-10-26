package org.example.takecareofmyfridgebe.domain.cooking.dto;

/**
 * 30일간 요리 통계 집계 결과 DTO.
 * 레시피 ID와 해당 레시피를 요리한 고유 회원 수를 담는다.
 */
public record PopularRecipeCookingCount(
        Long recipeId,
        long cookedMemberCount
) {}
