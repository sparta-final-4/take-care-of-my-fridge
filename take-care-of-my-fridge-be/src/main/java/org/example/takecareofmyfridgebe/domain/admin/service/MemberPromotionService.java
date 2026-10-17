package org.example.takecareofmyfridgebe.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.repository.AdminAuditLogRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberPromotionService {

    private final MemberRepository memberRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Transactional
    public void promote(Long actorId, Long targetId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (actor.getRole() != Member.Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Member target = memberRepository.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (target.getStatus() == Member.Status.SUSPENDED) {
            throw new BusinessException(ErrorCode.PROMOTION_SUSPENDED);
        }
        if (target.getRole() == Member.Role.ADMIN) {
            throw new BusinessException(ErrorCode.ALREADY_ADMIN);
        }
        target.promoteToAdmin();
        auditLogRepository.save(AdminAuditLog.create(actor, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, targetId, AdminAuditLog.Result.SUCCESS,
                Map.of("role", "USER"), Map.of("role", "ADMIN")));
    }
}
