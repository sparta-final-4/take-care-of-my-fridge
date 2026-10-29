package org.example.takecareofmyfridgebe.domain.deal.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisExecutionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealAnalysisExecutionFacadeTest {

    @Mock private DealAnalysisExecutionService executionService;
    @Mock private AdminAuditLogService auditLogService;
    @InjectMocks private DealAnalysisExecutionFacade facade;

    @Test
    void deniesUserAndRecordsDenial() {
        assertThatThrownBy(() -> facade.run(1L, Member.Role.USER,
                LocalDate.of(2030, 1, 7), DealAnalysis.Day.MON, 20))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_ACCESS_DENIED));
        verify(auditLogService).record(1L, AdminAuditLog.Action.DEAL_ANALYSIS,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, null, AdminAuditLog.Result.DENIED);
        verifyNoInteractions(executionService);
    }

    @Test
    void recordsFailedAnalysisAfterServiceRollback() {
        LocalDate week = LocalDate.of(2030, 1, 7);
        when(executionService.run(eq(1L), eq(week), eq(DealAnalysis.Day.MON), eq(20)))
                .thenThrow(new BusinessException(ErrorCode.DEAL_TRAINING_DATA_INSUFFICIENT));

        assertThatThrownBy(() -> facade.run(1L, Member.Role.ADMIN, week, DealAnalysis.Day.MON, 20))
                .isInstanceOf(BusinessException.class);
        verify(auditLogService).record(1L, AdminAuditLog.Action.DEAL_ANALYSIS,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, null, AdminAuditLog.Result.FAILED);
    }
}
