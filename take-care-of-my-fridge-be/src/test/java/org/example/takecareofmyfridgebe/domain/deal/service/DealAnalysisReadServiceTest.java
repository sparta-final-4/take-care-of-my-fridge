package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DealAnalysisReadServiceTest {

    private final DealAnalysisRepository analyses = mock(DealAnalysisRepository.class);
    private final DealCandidateRepository candidates = mock(DealCandidateRepository.class);
    private final DealAnalysisReadService service = new DealAnalysisReadService(analyses, candidates);

    @Test
    void readsAnalysisAndItsItems() {
        DealAnalysis analysis = DealAnalysis.builder().build();
        ReflectionTestUtils.setField(analysis, "id", 8L);
        when(analyses.findById(8L)).thenReturn(Optional.of(analysis));
        when(candidates.findByDealAnalysisIdOrderByIdAsc(8L)).thenReturn(List.of());

        assertThat(service.getById(8L).analysisId()).isEqualTo(8L);
    }

    @Test
    void missingAnalysisReturnsD002WithoutReadingItems() {
        when(analyses.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));
        verifyNoInteractions(candidates);
    }

    @Test
    void latestReadsOnlyRequestedMembersAnalysis() {
        DealAnalysis analysis = DealAnalysis.builder().build();
        ReflectionTestUtils.setField(analysis, "id", 12L);
        when(analyses.findTopByMemberIdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.of(analysis));
        when(candidates.findByDealAnalysisIdOrderByIdAsc(12L)).thenReturn(List.of());

        assertThat(service.getLatestForMember(7L).analysisId()).isEqualTo(12L);
    }

    @Test
    void memberWithoutAnalysisReturnsD002() {
        when(analyses.findTopByMemberIdOrderByCreatedAtDescIdDesc(7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestForMember(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));
        verifyNoInteractions(candidates);
    }
}
