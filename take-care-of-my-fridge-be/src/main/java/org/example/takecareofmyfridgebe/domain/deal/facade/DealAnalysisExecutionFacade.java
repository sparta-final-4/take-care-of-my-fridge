package org.example.takecareofmyfridgebe.domain.deal.facade;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisExecutionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealAnalysisExecutionFacade {

    private final DealAnalysisExecutionService executionService;
    private final AdminAuditLogService auditLogService;

    public DealAnalysisResponse run(Long actorId, Member.Role role, LocalDate targetWeekStart,
            DealAnalysis.Day day, int discountRate) {
        if (role != Member.Role.ADMIN) {
            record(actorId, AdminAuditLog.Result.DENIED);
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
        try {
            return executionService.run(actorId, targetWeekStart, day, discountRate);
        } catch (RuntimeException e) {
            record(actorId, AdminAuditLog.Result.FAILED);
            throw e;
        }
    }

    private void record(Long actorId, AdminAuditLog.Result result) {
        auditLogService.record(actorId, AdminAuditLog.Action.DEAL_ANALYSIS,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, null, result);
    }
}
