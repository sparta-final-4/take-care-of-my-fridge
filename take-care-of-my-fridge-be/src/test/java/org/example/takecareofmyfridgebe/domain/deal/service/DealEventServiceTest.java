package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealEventRequest;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventCreateService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DealEventServiceTest {

    private final DealAnalysisRepository analyses = mock(DealAnalysisRepository.class);
    private final DealCandidateRepository candidates = mock(DealCandidateRepository.class);
    private final SaleEventCreateService sales = mock(SaleEventCreateService.class);
    private final AdminAuditLogService audit = mock(AdminAuditLogService.class);
    private final DealEventService service = new DealEventService(analyses, candidates, sales, audit);
    private final LocalDateTime startsAt = LocalDateTime.of(2026, 11, 1, 10, 0);
    private final LocalDateTime endsAt = startsAt.plusHours(4);

    @Test
    void createsSaleAndRecordsItsIdOnlyAfterSuccess() {
        DealCandidate candidate = selectedCandidate();
        when(sales.createSpecialSale(any(SpecialSaleCreateRequest.class)))
                .thenReturn(new SaleEventsCreateResponse(List.of(55L)));

        var response = service.register(7L, 3L,
                new DealEventRequest(11L, SaleEventType.SPECIAL, startsAt, endsAt, null));

        assertThat(response.saleEventId()).isEqualTo(55L);
        assertThat(response.eventRequestStatus()).isEqualTo(DealCandidate.EventRequestStatus.APPROVED);
        assertThat(candidate.getPromotionId()).isEqualTo(55L);
        verify(sales).createSpecialSale(new SpecialSaleCreateRequest(startsAt, endsAt,
                List.of(new SpecialSaleItemRequest(21L, 30))));
        verify(audit).record(7L, AdminAuditLog.Action.EVENT_REQUEST,
                AdminAuditLog.TARGET_DEAL_CANDIDATE, 11L, AdminAuditLog.Result.SUCCESS);
    }

    @Test
    void doesNotCreateSaleForUnselectedCandidate() {
        DealCandidate candidate = selectedCandidate();
        candidate.review(DealCandidate.ReviewStatus.HELD);

        assertThatThrownBy(() -> service.register(7L, 3L,
                new DealEventRequest(11L, SaleEventType.SPECIAL, startsAt, endsAt, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DEAL_EVENT_NOT_AVAILABLE));
        verifyNoInteractions(sales, audit);
    }

    @Test
    void passesLimitedQuantityToFlashDrop() {
        selectedCandidate();
        when(sales.createFlashDrop(any(FlashDropCreateRequest.class)))
                .thenReturn(new SaleEventsCreateResponse(List.of(56L)));

        var response = service.register(7L, 3L,
                new DealEventRequest(11L, SaleEventType.FLASH_DROP, startsAt, endsAt, 8));

        assertThat(response.saleEventId()).isEqualTo(56L);
        verify(sales).createFlashDrop(new FlashDropCreateRequest(startsAt, endsAt,
                List.of(new FlashDropItemRequest(21L, 30, 8))));
    }

    @Test
    void saleFailureDoesNotMarkCandidateAsRegistered() {
        DealCandidate candidate = selectedCandidate();
        when(sales.createSpecialSale(any(SpecialSaleCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED));

        assertThatThrownBy(() -> service.register(7L, 3L,
                new DealEventRequest(11L, SaleEventType.SPECIAL, startsAt, endsAt, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED));
        assertThat(candidate.getEventRequestStatus()).isEqualTo(DealCandidate.EventRequestStatus.NONE);
        assertThat(candidate.getPromotionId()).isNull();
        verifyNoInteractions(audit);
    }

    @Test
    void syntheticAnalysisCannotCreateRealSale() {
        DealAnalysis analysis = DealAnalysis.builder().syntheticData(true).build();
        when(analyses.findByIdForUpdate(3L)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.register(7L, 3L,
                new DealEventRequest(11L, SaleEventType.SPECIAL, startsAt, endsAt, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DEAL_SYNTHETIC_ANALYSIS));
        verifyNoInteractions(sales, audit);
    }

    private DealCandidate selectedCandidate() {
        DealAnalysis analysis = DealAnalysis.builder().discountRate(30).build();
        ReflectionTestUtils.setField(analysis, "id", 3L);
        DealCandidate candidate = DealCandidate.builder()
                .dealAnalysis(analysis).productId(21L).build();
        ReflectionTestUtils.setField(candidate, "id", 11L);
        candidate.review(DealCandidate.ReviewStatus.SELECTED);
        when(analyses.findByIdForUpdate(3L)).thenReturn(Optional.of(analysis));
        when(candidates.findById(11L)).thenReturn(Optional.of(candidate));
        return candidate;
    }
}
