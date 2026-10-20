package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealReviewResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DealReviewService {

    private final DealAnalysisRepository analysisRepository;
    private final DealCandidateRepository candidateRepository;
    private final AdminAuditLogService auditLogService;

    @Transactional
    public DealReviewResponse save(Long actorId, Long analysisId, List<DealReviewRequest.Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        DealAnalysis analysis = analysisRepository.findByIdForUpdate(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));
        List<DealCandidate> candidates = candidateRepository.findByDealAnalysisIdOrderByIdAsc(analysisId);
        Map<Long, DealCandidate> byId = new HashMap<>();
        for (DealCandidate candidate : candidates) {
            byId.put(candidate.getId(), candidate);
        }
        Set<Long> seen = new HashSet<>();
        for (DealReviewRequest.Review review : reviews) {
            if (!seen.add(review.candidateId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            DealCandidate candidate = byId.get(review.candidateId());
            if (candidate == null) {
                throw new BusinessException(ErrorCode.DEAL_CANDIDATE_NOT_IN_ANALYSIS);
            }
            candidate.review(review.reviewStatus());
        }
        LocalDateTime savedAt = LocalDateTime.now();
        analysis.markReviewsSaved(savedAt);
        auditLogService.record(actorId, AdminAuditLog.Action.DEAL_REVIEW,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, analysisId, AdminAuditLog.Result.SUCCESS);
        return DealReviewResponse.from(savedAt, candidates);
    }
}
