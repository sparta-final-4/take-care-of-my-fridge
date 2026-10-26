package org.example.takecareofmyfridgebe.domain.deal.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventResponse;
import org.example.takecareofmyfridgebe.domain.deal.service.DealEventService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealEventFacade {

    private final DealEventService eventService;
    private final AdminAuditLogService auditLogService;

    public DealEventResponse register(Long actorId, Member.Role role, Long analysisId, DealEventRequest request) {
        if (role != Member.Role.ADMIN) {
            record(actorId, request.candidateId(), AdminAuditLog.Result.DENIED);
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
        try {
            return eventService.register(actorId, analysisId, request);
        } catch (RuntimeException e) {
            record(actorId, request.candidateId(), AdminAuditLog.Result.FAILED);
            throw e;
        }
    }

    private void record(Long actorId, Long candidateId, AdminAuditLog.Result result) {
        auditLogService.record(actorId, AdminAuditLog.Action.EVENT_REQUEST,
                AdminAuditLog.TARGET_DEAL_CANDIDATE, candidateId, result);
    }
}
