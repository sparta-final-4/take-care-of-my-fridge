package org.example.takecareofmyfridgebe.domain.forecast.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ForecastResponseTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);

    @Test
    void unconfirmedWeekHasOnlyPredictedItemsAndNullComparisonFields() {
        PredictionRun run = run(null);
        SalesForecast predicted = item(run, 1L, 1, 10, 7, 1, 8);
        SalesForecast entrant = item(run, 2L, null, null, 12, 2, 15);

        ForecastResponse response = ForecastResponse.from(run, List.of(entrant, predicted),
                null, WEEK.plusWeeks(2));

        assertThat(response.weekStart()).isEqualTo(WEEK);
        assertThat(response.weekEnd()).isEqualTo(WEEK.plusDays(6));
        assertThat(response.confirmed()).isFalse();
        assertThat(response.summary()).isNull();
        assertThat(response.previousWeekStart()).isNull();
        assertThat(response.nextWeekStart()).isEqualTo(WEEK.plusWeeks(2));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).actualQty()).isNull();
        assertThat(response.items().get(0).actualRank()).isNull();
        assertThat(response.items().get(0).status()).isNull();
    }

    @Test
    void confirmedWeekIncludesMatchesDropsEntrantsAndErrors() {
        PredictionRun run = run(LocalDateTime.of(2026, 10, 26, 6, 0));
        SalesForecast matched = item(run, 1L, 1, 10, 7, 1, 8);
        SalesForecast dropped = item(run, 2L, 2, 20, 18, null, 0);
        SalesForecast entered = item(run, 3L, null, null, 25, 2, 15);

        ForecastResponse response = ForecastResponse.from(run,
                List.of(entered, dropped, matched), WEEK.minusWeeks(1), null);

        assertThat(response.summary()).isEqualTo(new ForecastResponse.Summary(1, 2,
                11.0, 9.5, 1, 1));
        assertThat(response.items()).extracting(ForecastResponse.Item::productId)
                .containsExactly(1L, 2L, 3L);
        assertThat(response.items()).extracting(ForecastResponse.Item::status)
                .containsExactly(ForecastResponse.Status.MATCHED,
                        ForecastResponse.Status.DROPPED, ForecastResponse.Status.ENTERED);
        assertThat(response.items().get(0).qtyError()).isEqualTo(2);
        assertThat(response.items().get(1).actualQty()).isZero();
        assertThat(response.items().get(2).predictedRank()).isNull();
        assertThat(response.items().get(2).qtyError()).isNull();
    }

    @Test
    void baselineMatchUsesOnlyHighestTenBaselineQuantities() {
        PredictionRun run = run(LocalDateTime.of(2026, 10, 26, 6, 0));
        List<SalesForecast> forecasts = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            forecasts.add(item(run, id, null, null, 100 - (int) id, null, 0));
        }
        forecasts.add(item(run, 11L, 1, 5, 1, 1, 5));

        ForecastResponse response = ForecastResponse.from(run, forecasts, null, null);

        assertThat(response.summary().top10MatchCount()).isEqualTo(1);
        assertThat(response.summary().baselineTop10MatchCount()).isZero();
    }

    @Test
    void actualRankElevenIsDroppedAndRankTenIsEntered() {
        PredictionRun run = run(LocalDateTime.of(2026, 10, 26, 6, 0));
        SalesForecast dropped = item(run, 1L, 10, 4, 3, 11, 6);
        SalesForecast entered = item(run, 2L, null, null, 2, 10, 9);

        ForecastResponse response = ForecastResponse.from(
                run, List.of(entered, dropped), null, null);

        assertThat(response.summary().top10MatchCount()).isZero();
        assertThat(response.summary().enteredCount()).isEqualTo(1);
        assertThat(response.summary().droppedCount()).isEqualTo(1);
        assertThat(response.summary().meanAbsoluteError()).isEqualTo(2.0);
        assertThat(response.items()).extracting(ForecastResponse.Item::status)
                .containsExactly(ForecastResponse.Status.DROPPED, ForecastResponse.Status.ENTERED);
        assertThat(response.items().get(0).rankChange()).isEqualTo(-1);
        assertThat(response.items().get(0).qtyError()).isEqualTo(-2);
    }

    @Test
    void confirmedWeekTreatsMissingActualQtyAsZero() {
        PredictionRun run = run(LocalDateTime.of(2026, 10, 26, 6, 0));
        SalesForecast noSales = item(run, 1L, 1, 5, 3, null, null);

        ForecastResponse response = ForecastResponse.from(run, List.of(noSales), null, null);

        assertThat(response.summary().meanAbsoluteError()).isEqualTo(5.0);
        assertThat(response.summary().baselineMeanAbsoluteError()).isEqualTo(3.0);
        assertThat(response.items().get(0).actualQty()).isZero();
        assertThat(response.items().get(0).qtyError()).isEqualTo(5);
        assertThat(response.items().get(0).status()).isEqualTo(ForecastResponse.Status.DROPPED);
    }

    private PredictionRun run(LocalDateTime confirmedAt) {
        PredictionRun run = PredictionRun.builder().targetWeekStart(WEEK)
                .modelVersion("weekly-mlp-v1")
                .dataCutoffAt(LocalDateTime.of(2026, 10, 18, 18, 0))
                .confirmedAt(confirmedAt).syntheticData(true).build();
        ReflectionTestUtils.setField(run, "createdAt", LocalDateTime.of(2026, 10, 18, 22, 0));
        return run;
    }

    private SalesForecast item(PredictionRun run, Long productId, Integer predictedRank,
                               Integer predictedQty, int baselineQty,
                               Integer actualRank, Integer actualQty) {
        return SalesForecast.builder().predictionRun(run).productId(productId)
                .productName("상품 " + productId).predictedRank(predictedRank)
                .predictedQty(predictedQty).baselineQty(baselineQty)
                .actualRank(actualRank).actualQty(actualQty).build();
    }
}
