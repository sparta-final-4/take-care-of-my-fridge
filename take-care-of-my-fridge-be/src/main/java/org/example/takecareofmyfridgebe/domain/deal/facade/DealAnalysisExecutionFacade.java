package org.example.takecareofmyfridgebe.domain.deal.facade;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.facade.AdminActionGuard;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisExecutionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealAnalysisExecutionFacade {

    private final DealAnalysisExecutionService executionService;
    private final AdminActionGuard adminActionGuard;

    public DealAnalysisResponse run(Long actorId, Member.Role role, LocalDate targetWeekStart,
            DealAnalysis.Day day, int discountRate) {
        return adminActionGuard.run(actorId, role, AdminAuditLog.Action.DEAL_ANALYSIS,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, null,
                () -> executionService.run(actorId, targetWeekStart, day, discountRate));
    }
}
