package org.example.takecareofmyfridgebe.domain.cooking.repository;

import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingLogRepository extends JpaRepository<CookingLog, Long>, CookingLogRepositoryCustom {
}
