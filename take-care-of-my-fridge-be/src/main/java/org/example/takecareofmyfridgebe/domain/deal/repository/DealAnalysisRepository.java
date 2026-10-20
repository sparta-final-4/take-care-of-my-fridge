package org.example.takecareofmyfridgebe.domain.deal.repository;

import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealAnalysisRepository extends JpaRepository<DealAnalysis, Long> {

    Optional<DealAnalysis> findTopByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);
}
