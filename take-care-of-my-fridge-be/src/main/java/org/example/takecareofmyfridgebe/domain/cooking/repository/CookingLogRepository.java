package org.example.takecareofmyfridgebe.domain.cooking.repository;

import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CookingLogRepository extends JpaRepository<CookingLog, Long>, CookingLogRepositoryCustom {

    Optional<CookingLog> findByIdAndMemberId(Long id, Long memberId);
}
