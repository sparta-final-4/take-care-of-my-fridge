package org.example.takecareofmyfridgebe.domain.forecast.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;

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

    public static ForecastResponse from(PredictionRun run, List<SalesForecast> forecasts,
                                        LocalDate previousWeekStart, LocalDate nextWeekStart) {
        boolean confirmed = run.getConfirmedAt() != null;
        List<SalesForecast> predictedTop = forecasts.stream()
                .filter(ForecastResponse::isPredictedTop)
                .sorted(Comparator.comparing(SalesForecast::getPredictedRank))
                .toList();
        List<SalesForecast> entrants = confirmed ? forecasts.stream()
                .filter(item -> !isPredictedTop(item) && isActualTop(item))
                .sorted(Comparator.comparing(SalesForecast::getActualRank))
                .toList() : List.of();
        List<Item> items = Stream.concat(predictedTop.stream(), entrants.stream())
                .map(item -> toItem(item, confirmed))
                .toList();

        return new ForecastResponse(run.getTargetWeekStart(), run.getTargetWeekStart().plusDays(6),
                run.getModelVersion(), run.getDataCutoffAt(), run.getCreatedAt(),
                confirmed, run.getConfirmedAt(), run.isSyntheticData(),
                previousWeekStart, nextWeekStart,
                confirmed ? summarize(forecasts, predictedTop, entrants.size()) : null,
                items);
    }

    private static Summary summarize(List<SalesForecast> forecasts,
                                     List<SalesForecast> predictedTop, int enteredCount) {
        int matches = (int) predictedTop.stream().filter(ForecastResponse::isActualTop).count();
        int baselineMatches = (int) forecasts.stream()
                .sorted(Comparator.comparingInt(SalesForecast::getBaselineQty).reversed()
                        .thenComparing(SalesForecast::getProductId))
                .limit(10)
                .filter(ForecastResponse::isActualTop)
                .count();
        double predictionError = predictedTop.stream()
                .mapToInt(item -> Math.abs(item.getPredictedQty() - item.getActualQty()))
                .average().orElse(0);
        double baselineError = predictedTop.stream()
                .mapToInt(item -> Math.abs(item.getBaselineQty() - item.getActualQty()))
                .average().orElse(0);
        return new Summary(matches, baselineMatches,
                roundOneDecimal(predictionError), roundOneDecimal(baselineError),
                enteredCount, predictedTop.size() - matches);
    }

    private static Item toItem(SalesForecast item, boolean confirmed) {
        boolean predictedTop = isPredictedTop(item);
        Integer predictedRank = predictedTop ? item.getPredictedRank() : null;
        Integer predictedQty = predictedTop ? item.getPredictedQty() : null;
        Integer actualRank = confirmed ? item.getActualRank() : null;
        Integer actualQty = confirmed ? item.getActualQty() : null;
        Integer rankChange = confirmed && predictedRank != null && actualRank != null
                ? predictedRank - actualRank : null;
        Integer qtyError = confirmed && predictedQty != null && actualQty != null
                ? predictedQty - actualQty : null;
        Status status = !confirmed ? null
                : !predictedTop ? Status.ENTERED
                : isActualTop(item) ? Status.MATCHED
                : Status.DROPPED;
        return new Item(item.getProductId(), item.getProductName(),
                predictedRank, predictedQty, item.getBaselineQty(), actualRank, actualQty,
                rankChange, qtyError, status);
    }

    private static boolean isPredictedTop(SalesForecast item) {
        return item.getPredictedRank() != null && item.getPredictedRank() <= 10;
    }

    private static boolean isActualTop(SalesForecast item) {
        return item.getActualRank() != null && item.getActualRank() <= 10;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

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
