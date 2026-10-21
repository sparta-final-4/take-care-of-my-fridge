package org.example.takecareofmyfridgebe.domain.deal.repository;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealCandidateRepository extends JpaRepository<DealCandidate, Long> {

    List<DealCandidate> findByDealAnalysisIdOrderByIdAsc(Long analysisId);
}
