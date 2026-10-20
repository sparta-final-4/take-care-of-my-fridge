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
        } catch (BusinessException e) {
            record(actorId, analysisId, AdminAuditLog.Result.FAILED);
            throw e;
        }
    }

    private void record(Long actorId, Long analysisId, AdminAuditLog.Result result) {
        auditLogService.record(actorId, AdminAuditLog.Action.DEAL_REVIEW,
                "DealAnalysis", analysisId, result);
    }
}
