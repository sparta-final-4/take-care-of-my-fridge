package org.example.takecareofmyfridgebe.domain.cooking.dto;

/**
 * 메인 화면 인기 레시피 응답 DTO.
 */
public record PopularRecipeResponse(
        Long recipeId,
        String recipeName,
        String imageUrl,
        String tipSummary,
        long cookedMemberCount,
        Double averageRating,
        long reviewCount
) {}
