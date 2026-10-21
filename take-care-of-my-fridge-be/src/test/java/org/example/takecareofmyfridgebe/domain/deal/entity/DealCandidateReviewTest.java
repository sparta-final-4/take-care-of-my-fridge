package org.example.takecareofmyfridgebe.domain.deal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DealCandidateReviewTest {

    @Test
    void candidateCanBeSelectedAndHeld() {
        DealCandidate candidate = DealCandidate.builder().build();

        candidate.review(DealCandidate.ReviewStatus.SELECTED);
        assertThat(candidate.getReviewStatus()).isEqualTo(DealCandidate.ReviewStatus.SELECTED);
        candidate.review(DealCandidate.ReviewStatus.HELD);
        assertThat(candidate.getReviewStatus()).isEqualTo(DealCandidate.ReviewStatus.HELD);
    }

    @Test
    void excludedProductCannotBeReviewed() {
        DealCandidate excluded = DealCandidate.builder().excluded(true).build();

        assertThatThrownBy(() -> excluded.review(DealCandidate.ReviewStatus.SELECTED))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.DEAL_CANDIDATE_NOT_IN_ANALYSIS));
    }

    @Test
    void requestedCandidateCannotChangeReview() {
        DealCandidate candidate = DealCandidate.builder().build();
        ReflectionTestUtils.setField(candidate, "eventRequestStatus",
                DealCandidate.EventRequestStatus.REQUESTED);

        assertThatThrownBy(() -> candidate.review(DealCandidate.ReviewStatus.HELD))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.DEAL_REVIEW_LOCKED));
    }

    @Test
    void unreviewedIsNotAnAcceptedUpdate() {
        DealCandidate candidate = DealCandidate.builder().build();

        assertThatThrownBy(() -> candidate.review(DealCandidate.ReviewStatus.UNREVIEWED))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
