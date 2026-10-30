package org.example.takecareofmyfridgebe.domain.deal.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.facade.AdminActionGuard;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventResponse;
import org.example.takecareofmyfridgebe.domain.deal.service.DealEventService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealEventFacade {

    private final DealEventService eventService;
    private final AdminActionGuard adminActionGuard;

    public DealEventResponse register(Long actorId, Member.Role role, Long analysisId, DealEventRequest request) {
        return adminActionGuard.run(actorId, role, AdminAuditLog.Action.EVENT_REQUEST,
                AdminAuditLog.TARGET_DEAL_CANDIDATE, request.candidateId(),
                () -> eventService.register(actorId, analysisId, request));
    }
}
