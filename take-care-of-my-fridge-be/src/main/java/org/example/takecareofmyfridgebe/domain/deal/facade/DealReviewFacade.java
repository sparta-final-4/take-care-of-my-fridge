package org.example.takecareofmyfridgebe.domain.deal.facade;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.facade.AdminActionGuard;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewResponse;
import org.example.takecareofmyfridgebe.domain.deal.service.DealReviewService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealReviewFacade {

    private final DealReviewService reviewService;
    private final AdminActionGuard adminActionGuard;

    public DealReviewResponse save(Long actorId, Member.Role actorRole, Long analysisId,
                                   List<DealReviewRequest.Review> reviews) {
        return adminActionGuard.run(actorId, actorRole, AdminAuditLog.Action.DEAL_REVIEW,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, analysisId,
                () -> reviewService.save(actorId, analysisId, reviews));
    }
}
