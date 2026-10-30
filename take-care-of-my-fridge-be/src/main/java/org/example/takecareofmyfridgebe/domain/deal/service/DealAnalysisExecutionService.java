package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DealAnalysisExecutionService {

    private final MemberRepository memberRepository;
    private final DealAnalysisRepository analysisRepository;
    private final DealCandidateRepository candidateRepository;
    private final DealTrainingObservationService observationService;
    private final DealProductInputService productInputService;
    private final DealSalesSummaryService salesSummaryService;
    private final DealClickInputService clickInputService;
    private final DealCandidateCalculator candidateCalculator;
    private final AdminAuditLogService auditLogService;

    @Transactional
    public DealAnalysisResponse run(Long actorId, LocalDate targetWeekStart,
            DealAnalysis.Day day, int discountRate) {
        LocalDateTime asOf = LocalDateTime.now(SaleEvent.ZONE);
        if (targetWeekStart == null || targetWeekStart.getDayOfWeek() != DayOfWeek.MONDAY
                || day == null || discountRate < 1 || discountRate > 99
                || targetWeekStart.plusDays(day.ordinal()).isBefore(asOf.toLocalDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        DayOfWeek dayOfWeek = DayOfWeek.of(day.ordinal() + 1);
        DealDiscountModel model = DealDiscountModel.train(observationService.getObservations(asOf));
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        List<DealProductInput> products = productInputService.getProducts();
        Map<Long, DealSalesSummary> sales = salesSummaryService.getByProduct(
                targetWeekStart, dayOfWeek, asOf);
        Map<Long, Long> clicks = clickInputService.getSameWeekdayClicks(
                targetWeekStart, dayOfWeek, asOf);
        DealAnalysis analysis = analysisRepository.save(DealAnalysis.builder()
                .member(actor).targetWeekStart(targetWeekStart).dayOfWeek(day)
                .discountRate(discountRate).minStock(DealExclusionPolicy.MIN_STOCK)
                .modelVersion(DealDiscountModel.VERSION).dataCutoffAt(asOf)
                .syntheticData(false).build());
        List<DealCandidate> candidates = candidateRepository.saveAll(candidateCalculator.calculate(
                analysis, products, sales, clicks, model));
        auditLogService.record(actorId, AdminAuditLog.Action.DEAL_ANALYSIS,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, analysis.getId(), AdminAuditLog.Result.SUCCESS);
        return DealAnalysisResponse.from(analysis, candidates);
    }
}
