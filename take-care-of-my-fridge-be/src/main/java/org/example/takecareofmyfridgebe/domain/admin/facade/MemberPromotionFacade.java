package org.example.takecareofmyfridgebe.domain.admin.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.MemberPromotionService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberPromotionFacade {

    private final MemberPromotionService memberPromotionService;
    private final AdminActionGuard adminActionGuard;

    public void promote(Long actorId, Member.Role actorRole, Long targetId) {
        adminActionGuard.run(actorId, actorRole, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, targetId, () -> {
                    memberPromotionService.promote(actorId, targetId);
                    return null;
                });
    }
}
