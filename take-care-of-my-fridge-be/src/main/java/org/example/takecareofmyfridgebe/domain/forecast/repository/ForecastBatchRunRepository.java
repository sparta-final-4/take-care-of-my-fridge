package org.example.takecareofmyfridgebe.domain.forecast.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForecastBatchRunRepository extends JpaRepository<ForecastBatchRun, Long> {
    Optional<ForecastBatchRun> findByTargetWeekStart(LocalDate targetWeekStart);
}
