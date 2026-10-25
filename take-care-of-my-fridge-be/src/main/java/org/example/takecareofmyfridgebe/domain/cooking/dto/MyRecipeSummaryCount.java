package org.example.takecareofmyfridgebe.domain.cooking.dto;

public record MyRecipeSummaryCount(
        long recipeCount,
        long totalCookCount,
        long writtenCount,
        long notWrittenCount
) {
    public MyRecipeSummaryCount(
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

    public static MyRecipeSummaryCount empty() {
        return new MyRecipeSummaryCount(0L, 0L, 0L, 0L);
    }
}
