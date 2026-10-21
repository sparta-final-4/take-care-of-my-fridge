package org.example.takecareofmyfridgebe.domain.deal.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealAnalysisRepository extends JpaRepository<DealAnalysis, Long> {

    Optional<DealAnalysis> findTopByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select analysis from DealAnalysis analysis where analysis.id = :id")
    Optional<DealAnalysis> findByIdForUpdate(@Param("id") Long id);
}
