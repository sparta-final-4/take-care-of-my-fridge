package org.example.takecareofmyfridgebe.domain.deal.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealAnalysisReadService {

    private final DealAnalysisRepository analysisRepository;
    private final DealCandidateRepository candidateRepository;

    public DealAnalysisResponse getById(Long analysisId) {
        DealAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));
        return DealAnalysisResponse.from(analysis,
                candidateRepository.findByDealAnalysisIdOrderByIdAsc(analysisId));
    }
}
