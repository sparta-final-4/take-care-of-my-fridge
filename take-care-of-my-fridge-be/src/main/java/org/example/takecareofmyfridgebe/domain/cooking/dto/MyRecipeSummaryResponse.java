package org.example.takecareofmyfridgebe.domain.cooking.dto;

public record MyRecipeSummaryResponse(
        long recipeCount,
        long totalCookCount,
        long writtenCount,
        long notWrittenCount
) {
    /**
     * QueryDSL Projections.constructor 전용 생성자.
     */
    public MyRecipeSummaryResponse(
            Long recipeCount,
            Long totalCookCount,
            Long writtenCount
    ) {
        this(
                recipeCount != null ? recipeCount : 0L,
                totalCookCount != null ? totalCookCount : 0L,
                writtenCount != null ? writtenCount : 0L,
                (recipeCount != null ? recipeCount : 0L) - (writtenCount != null ? writtenCount : 0L)
        );
    }

    public static MyRecipeSummaryResponse empty() {
        return new MyRecipeSummaryResponse(0L, 0L, 0L, 0L);
    }
}
