package org.example.takecareofmyfridgebe.domain.admin.dto;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
        Long auditLogId,
        LocalDateTime createdAt,
        String actorEmail,
        Member.Role actorRole,
        AdminAuditLog.Action action,
        String targetType,
        Long targetId,
        AdminAuditLog.Result result,
        Map<String, String> beforeValue,
        Map<String, String> afterValue
) {
    public static AuditLogResponse from(AdminAuditLog log) {
        return new AuditLogResponse(log.getId(), log.getCreatedAt(), log.getMember().getEmail(),
                log.getActorRole() == null ? log.getMember().getRole() : log.getActorRole(),
                log.getAction(), log.getTargetType(), log.getTargetId(), log.getResult(),
                log.getBeforeValue(), log.getAfterValue());
    }
}
