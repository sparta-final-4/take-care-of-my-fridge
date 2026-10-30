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
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final AdminAuditLogRepository auditLogRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void record(Long actorId, AdminAuditLog.Action action, String targetType,
                       Long targetId, AdminAuditLog.Result result) {
        record(actorId, action, targetType, targetId, result, null, null);
    }

    // 값 변경 작업은 바뀌기 전·후 값을 함께 남긴다.
    @Transactional
    public void record(Long actorId, AdminAuditLog.Action action, String targetType,
                       Long targetId, AdminAuditLog.Result result,
                       Map<String, String> beforeValue, Map<String, String> afterValue) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        auditLogRepository.save(AdminAuditLog.builder()
                .member(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .result(result)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }
}
