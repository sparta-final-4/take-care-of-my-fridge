package org.example.takecareofmyfridgebe.domain.deal.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.facade.AdminActionGuard;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.service.DealReviewService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class DealReviewFacadeTest {

    private final DealReviewService service = mock(DealReviewService.class);
    private final AdminAuditLogService audit = mock(AdminAuditLogService.class);
    private final DealReviewFacade facade = new DealReviewFacade(service, new AdminActionGuard(audit));
    private final List<DealReviewRequest.Review> reviews = List.of(
            new DealReviewRequest.Review(11L, DealCandidate.ReviewStatus.SELECTED));

    @Test
    void returnsSavedResultWithoutDuplicateAudit() {
        DealReviewResponse saved = new DealReviewResponse(LocalDateTime.of(2026, 10, 20, 10, 0),
                new DealAnalysisResponse.ReviewCounts(1, 0, 0));
        when(service.save(7L, 3L, reviews)).thenReturn(saved);

        assertThat(facade.save(7L, Member.Role.ADMIN, 3L, reviews)).isEqualTo(saved);
        verifyNoInteractions(audit);
    }

    @Test
    void recordsBusinessFailure() {
        when(service.save(7L, 3L, reviews))
                .thenThrow(new BusinessException(ErrorCode.DEAL_REVIEW_LOCKED));

        assertThatThrownBy(() -> facade.save(7L, Member.Role.ADMIN, 3L, reviews))
                .isInstanceOf(BusinessException.class);
        verify(audit).record(7L, AdminAuditLog.Action.DEAL_REVIEW,
                "DealAnalysis", 3L, AdminAuditLog.Result.FAILED);
    }

    @Test
    void recordsUnexpectedFailure() {
        when(service.save(7L, 3L, reviews)).thenThrow(new IllegalStateException("lock timeout"));

        assertThatThrownBy(() -> facade.save(7L, Member.Role.ADMIN, 3L, reviews))
                .isInstanceOf(IllegalStateException.class);
        verify(audit).record(7L, AdminAuditLog.Action.DEAL_REVIEW,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, 3L, AdminAuditLog.Result.FAILED);
    }

    @Test
    void recordsDeniedRoleBeforeSaving() {
        assertThatThrownBy(() -> facade.save(7L, Member.Role.USER, 3L, reviews))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_ACCESS_DENIED));
        verifyNoInteractions(service);
        verify(audit).record(7L, AdminAuditLog.Action.DEAL_REVIEW,
                "DealAnalysis", 3L, AdminAuditLog.Result.DENIED);
    }
}
