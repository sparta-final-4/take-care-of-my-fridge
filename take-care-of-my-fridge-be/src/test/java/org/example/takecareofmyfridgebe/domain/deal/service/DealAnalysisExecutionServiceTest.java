package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DealAnalysisExecutionServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private DealAnalysisRepository analysisRepository;
    @Mock private DealCandidateRepository candidateRepository;
    @Mock private DealTrainingObservationService observationService;
    @Mock private DealProductInputService productInputService;
    @Mock private DealSalesSummaryService salesSummaryService;
    @Mock private DealClickInputService clickInputService;
    @Mock private DealCandidateCalculator candidateCalculator;
    @Mock private AdminAuditLogService auditLogService;
    @InjectMocks private DealAnalysisExecutionService service;

    @Test
    void abortsWithoutSavingWhenRealSaleHistoryIsInsufficient() {
        LocalDate week = nextWeek();
        given(observationService.getObservations(any(LocalDateTime.class))).willReturn(List.of());

        assertThatThrownBy(() -> service.run(1L, week, DealAnalysis.Day.WED, 20))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DEAL_TRAINING_DATA_INSUFFICIENT));
        verifyNoInteractions(analysisRepository, candidateRepository);
    }

    @Test
    void savesRealAnalysisAndInputsAtOneCutoff() {
        LocalDate week = nextWeek();
        Member admin = Member.builder().email("admin@example.com").passwordHash("hash")
                .nickname("admin").role(Member.Role.ADMIN).build();
        given(observationService.getObservations(any(LocalDateTime.class))).willReturn(List.of(
                new DealDiscountModel.Observation(10, 10, 12),
                new DealDiscountModel.Observation(20, 10, 14),
                new DealDiscountModel.Observation(30, 10, 16)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(productInputService.getProducts()).willReturn(List.of(
                new DealProductInput(2L, "상품", 1000, ProductStatus.ON_SALE, 100)));
        given(salesSummaryService.getByProduct(eq(week), eq(DayOfWeek.WEDNESDAY), any(LocalDateTime.class)))
                .willReturn(Map.of());
        given(clickInputService.getSameWeekdayClicks(eq(week), eq(DayOfWeek.WEDNESDAY), any(LocalDateTime.class)))
                .willReturn(Map.of());
        given(analysisRepository.save(any(DealAnalysis.class))).willAnswer(call -> {
            DealAnalysis analysis = call.getArgument(0);
            ReflectionTestUtils.setField(analysis, "id", 13L);
            return analysis;
        });
        given(candidateRepository.saveAll(any())).willReturn(List.of());

        var result = service.run(1L, week, DealAnalysis.Day.WED, 20);

        assertThat(result.analysisId()).isEqualTo(13L);
        assertThat(result.syntheticData()).isFalse();
        assertThat(result.modelVersion()).isEqualTo(DealDiscountModel.VERSION);
        assertThat(result.targetWeekStart()).isEqualTo(week);
        verify(auditLogService).record(1L, AdminAuditLog.Action.DEAL_ANALYSIS,
                AdminAuditLog.TARGET_DEAL_ANALYSIS, 13L, AdminAuditLog.Result.SUCCESS);
    }

    private LocalDate nextWeek() {
        return LocalDate.now(SaleEvent.ZONE).plusWeeks(1).with(DayOfWeek.MONDAY);
    }
}
