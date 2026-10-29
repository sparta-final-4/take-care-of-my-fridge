package org.example.takecareofmyfridgebe.domain.deal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DealAnalysisResponseTest {

    @Test
    void separatesExcludedAndOrdersCandidatesByClickPurchaseGap() {
        DealAnalysis analysis = DealAnalysis.builder()
                .targetWeekStart(LocalDate.of(2026, 10, 26))
                .dayOfWeek(DealAnalysis.Day.WED).discountRate(30).minStock(50)
                .modelVersion("model-v1")
                .dataCutoffAt(LocalDateTime.of(2026, 10, 20, 6, 0))
                .syntheticData(true).build();
        ReflectionTestUtils.setField(analysis, "id", 3L);
        DealCandidate smaller = candidate(analysis, 11L, 4L, false, 12);
        DealCandidate larger = candidate(analysis, 12L, 5L, false, 20);
        DealCandidate excluded = candidate(analysis, 13L, 6L, true, null);
        ReflectionTestUtils.setField(smaller, "searchClicks", 50);
        ReflectionTestUtils.setField(smaller, "purchases", 20);
        ReflectionTestUtils.setField(larger, "searchClicks", 20);
        ReflectionTestUtils.setField(larger, "purchases", 10);

        DealAnalysisResponse response = DealAnalysisResponse.from(
                analysis, List.of(smaller, excluded, larger));

        assertThat(response.analysisId()).isEqualTo(3L);
        assertThat(response.syntheticData()).isTrue();
        assertThat(response.reviewCounts()).isEqualTo(
                new DealAnalysisResponse.ReviewCounts(0, 0, 2));
        assertThat(response.candidates()).extracting(DealAnalysisResponse.Candidate::candidateId)
                .containsExactly(11L, 12L);
        assertThat(response.excluded()).extracting(DealAnalysisResponse.Excluded::candidateId)
                .containsExactly(13L);
    }

    @Test
    void reviewCountsSkipExcludedAndTreatMissingStatusAsUnreviewed() {
        DealAnalysis analysis = DealAnalysis.builder().build();
        DealCandidate selected = candidate(analysis, 21L, 7L, false, 5);
        selected.review(DealCandidate.ReviewStatus.SELECTED);
        DealCandidate held = candidate(analysis, 22L, 8L, false, 4);
        held.review(DealCandidate.ReviewStatus.HELD);
        DealCandidate missing = candidate(analysis, 23L, 9L, false, 3);
        ReflectionTestUtils.setField(missing, "reviewStatus", null);
        DealCandidate excluded = candidate(analysis, 24L, 10L, true, null);

        assertThat(DealAnalysisResponse.ReviewCounts.from(List.of(selected, held, missing, excluded)))
                .isEqualTo(new DealAnalysisResponse.ReviewCounts(1, 1, 1));
    }

    private DealCandidate candidate(DealAnalysis analysis, Long id, Long productId,
                                    boolean excluded, Integer increase) {
        DealCandidate item = DealCandidate.builder().dealAnalysis(analysis).productId(productId)
                .productName("상품 " + productId).excluded(excluded)
                .excludeReason(excluded ? DealCandidate.ExcludeReason.LOW_STOCK : null)
                .expectedIncrease(increase).build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
