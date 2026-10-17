package org.example.takecareofmyfridgebe.domain.admin.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.admin.service.MemberPromotionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberPromotionFacade {

    private final MemberPromotionService memberPromotionService;
    private final AdminAuditLogService auditLogService;

    public void promote(Long actorId, Member.Role actorRole, Long targetId) {
        if (actorRole != Member.Role.ADMIN) {
            record(actorId, targetId, AdminAuditLog.Result.DENIED);
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
        try {
            memberPromotionService.promote(actorId, targetId);
        } catch (BusinessException e) {
            record(actorId, targetId, AdminAuditLog.Result.FAILED);
            throw e;
        }
    }

    private void record(Long actorId, Long targetId, AdminAuditLog.Result result) {
        auditLogService.record(actorId, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, targetId, result);
    }
}
