package org.example.takecareofmyfridgebe.domain.deal.facade;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewResponse;
import org.example.takecareofmyfridgebe.domain.deal.service.DealReviewService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealReviewFacade {

    private final DealReviewService reviewService;
    private final AdminAuditLogService auditLogService;

    public DealReviewResponse save(Long actorId, Member.Role actorRole, Long analysisId,
                                   List<DealReviewRequest.Review> reviews) {
        if (actorRole != Member.Role.ADMIN) {
            record(actorId, analysisId, AdminAuditLog.Result.DENIED);
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
        try {
            return reviewService.save(actorId, analysisId, reviews);
        } catch (RuntimeException e) {
            // 비즈니스 오류뿐 아니라 락 대기 초과 같은 예상 밖 오류도 실패 이력을 남긴다.
            record(actorId, analysisId, AdminAuditLog.Result.FAILED);
            throw e;
        }
    }

    private void record(Long actorId, Long analysisId, AdminAuditLog.Result result) {
        auditLogService.record(actorId, AdminAuditLog.Action.DEAL_REVIEW,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, analysisId, result);
    }
}
