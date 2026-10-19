package org.example.takecareofmyfridgebe.domain.forecast.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRunRepository extends JpaRepository<PredictionRun, Long> {

    Optional<PredictionRun> findByTargetWeekStart(LocalDate targetWeekStart);

    Optional<PredictionRun> findTopByOrderByTargetWeekStartDesc();

    Optional<PredictionRun> findTopByTargetWeekStartLessThanOrderByTargetWeekStartDesc(LocalDate targetWeekStart);

    Optional<PredictionRun> findTopByTargetWeekStartGreaterThanOrderByTargetWeekStartAsc(LocalDate targetWeekStart);
}
