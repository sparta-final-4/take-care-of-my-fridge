package org.example.takecareofmyfridgebe.domain.admin.facade;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

// 관리자 작업의 권한 확인과 거부·실패 이력을 한곳에서 남긴다.
// 성공 이력은 작업과 함께 저장돼야 하므로 각 서비스가 같은 트랜잭션에서 남긴다.
@Component
@RequiredArgsConstructor
public class AdminActionGuard {

    private final AdminAuditLogService auditLogService;

    public <T> T run(Long actorId, Member.Role actorRole, AdminAuditLog.Action action,
                     String targetType, Long targetId, Supplier<T> work) {
        if (actorRole != Member.Role.ADMIN) {
            auditLogService.record(actorId, action, targetType, targetId, AdminAuditLog.Result.DENIED);
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
        try {
            return work.get();
        } catch (RuntimeException e) {
            // 비즈니스 오류뿐 아니라 락 대기 초과 같은 예상 밖 오류도 실패 이력을 남긴다.
            auditLogService.record(actorId, action, targetType, targetId, AdminAuditLog.Result.FAILED);
            throw e;
        }
    }
}
