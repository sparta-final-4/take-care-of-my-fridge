package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DealReviewServiceTest {

    private final DealAnalysisRepository analyses = mock(DealAnalysisRepository.class);
    private final DealCandidateRepository candidates = mock(DealCandidateRepository.class);
    private final AdminAuditLogService audit = mock(AdminAuditLogService.class);
    private final DealReviewService service = new DealReviewService(analyses, candidates, audit);

    @Test
    void updatesOnlySubmittedCandidateAndRecordsSuccess() {
        DealAnalysis analysis = DealAnalysis.builder().build();
        DealCandidate selected = candidate(analysis, 11L);
        DealCandidate untouched = candidate(analysis, 12L);
        when(analyses.findByIdForUpdate(3L)).thenReturn(Optional.of(analysis));
        when(candidates.findByDealAnalysisIdOrderByIdAsc(3L))
                .thenReturn(List.of(selected, untouched));

        var response = service.save(7L, 3L,
                List.of(new DealReviewRequest.Review(11L, DealCandidate.ReviewStatus.SELECTED)));

        assertThat(selected.getReviewStatus()).isEqualTo(DealCandidate.ReviewStatus.SELECTED);
        assertThat(untouched.getReviewStatus()).isEqualTo(DealCandidate.ReviewStatus.UNREVIEWED);
        assertThat(response.reviewCounts().selected()).isEqualTo(1);
        assertThat(response.reviewCounts().unreviewed()).isEqualTo(1);
        assertThat(analysis.getLastSavedAt()).isEqualTo(response.lastSavedAt());
        verify(audit).record(7L, AdminAuditLog.Action.DEAL_REVIEW,
                "DealAnalysis", 3L, AdminAuditLog.Result.SUCCESS);
    }

    @Test
    void rejectsCandidateOutsideAnalysis() {
        DealAnalysis analysis = DealAnalysis.builder().build();
        when(analyses.findByIdForUpdate(3L)).thenReturn(Optional.of(analysis));
        when(candidates.findByDealAnalysisIdOrderByIdAsc(3L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.save(7L, 3L,
                List.of(new DealReviewRequest.Review(99L, DealCandidate.ReviewStatus.SELECTED))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.DEAL_CANDIDATE_NOT_IN_ANALYSIS));
        assertThat(analysis.getLastSavedAt()).isNull();
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsDuplicateCandidateIds() {
        DealAnalysis analysis = DealAnalysis.builder().build();
        DealCandidate candidate = candidate(analysis, 11L);
        when(analyses.findByIdForUpdate(3L)).thenReturn(Optional.of(analysis));
        when(candidates.findByDealAnalysisIdOrderByIdAsc(3L)).thenReturn(List.of(candidate));
        var review = new DealReviewRequest.Review(11L, DealCandidate.ReviewStatus.SELECTED);

        assertThatThrownBy(() -> service.save(7L, 3L, List.of(review, review)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(audit);
    }

    private DealCandidate candidate(DealAnalysis analysis, Long id) {
        DealCandidate candidate = DealCandidate.builder().dealAnalysis(analysis).build();
        ReflectionTestUtils.setField(candidate, "id", id);
        return candidate;
    }
}
