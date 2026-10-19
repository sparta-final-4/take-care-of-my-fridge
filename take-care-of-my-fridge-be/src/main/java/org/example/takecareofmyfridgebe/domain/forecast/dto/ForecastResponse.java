package org.example.takecareofmyfridgebe.domain.forecast.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ForecastResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        String modelVersion,
        LocalDateTime dataCutoffAt,
        LocalDateTime createdAt,
        boolean confirmed,
        LocalDateTime confirmedAt,
        boolean syntheticData,
        LocalDate previousWeekStart,
        LocalDate nextWeekStart,
        Summary summary,
        List<Item> items
) {

    public record Summary(
            int top10MatchCount,
            int baselineTop10MatchCount,
            double meanAbsoluteError,
            double baselineMeanAbsoluteError,
            int enteredCount,
            int droppedCount
    ) {}

    public record Item(
            Long productId,
            String productName,
            Integer predictedRank,
            Integer predictedQty,
            int baselineQty,
            Integer actualRank,
            Integer actualQty,
            Integer rankChange,
            Integer qtyError,
            Status status
    ) {}

    public enum Status {
        MATCHED, DROPPED, ENTERED
    }
}
