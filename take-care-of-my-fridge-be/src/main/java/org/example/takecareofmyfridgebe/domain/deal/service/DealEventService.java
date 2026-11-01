package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventRequest;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventCreateService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DealEventService {

    private final DealAnalysisRepository analysisRepository;
    private final DealCandidateRepository candidateRepository;
    private final SaleEventCreateService saleEventCreateService;
    private final AdminAuditLogService auditLogService;
    private final Environment environment;

    @Transactional
    public DealEventResponse register(Long actorId, Long analysisId, DealEventRequest request) {
        DealAnalysis analysis = analysisRepository.findByIdForUpdate(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));
        if (analysis.isSyntheticData() && !"dev".equals(environment.getProperty("APP_ENV"))) {
            throw new BusinessException(ErrorCode.DEAL_SYNTHETIC_ANALYSIS);
        }
        DealCandidate candidate = candidateRepository.findById(request.candidateId())
                .filter(item -> item.getDealAnalysis().getId().equals(analysisId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DEAL_CANDIDATE_NOT_IN_ANALYSIS));
        candidate.requireEventAvailable();

        Long eventId;
        if (request.type() == SaleEventType.FLASH_DROP) {
            eventId = saleEventCreateService.createFlashDrop(new FlashDropCreateRequest(
                    request.startsAt(), request.endsAt(), List.of(new FlashDropItemRequest(
                    candidate.getProductId(), analysis.getDiscountRate(), request.limitedQuantity()))))
                    .saleEventIds().get(0);
        } else {
            eventId = saleEventCreateService.createSpecialSale(new SpecialSaleCreateRequest(
                    request.startsAt(), request.endsAt(), List.of(new SpecialSaleItemRequest(
                    candidate.getProductId(), analysis.getDiscountRate()))))
                    .saleEventIds().get(0);
        }
        candidate.registerEvent(eventId, LocalDateTime.now());
        auditLogService.record(actorId, AdminAuditLog.Action.EVENT_REQUEST,
                AdminAuditLog.TARGET_DEAL_CANDIDATE, candidate.getId(), AdminAuditLog.Result.SUCCESS);
        return DealEventResponse.from(candidate);
    }
}
