package org.example.takecareofmyfridgebe.domain.forecast.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PredictionRunRepository extends JpaRepository<PredictionRun, Long> {

    Optional<PredictionRun> findByTargetWeekStart(LocalDate targetWeekStart);
    Optional<PredictionRun> findByTargetWeekStartAndSyntheticDataFalse(LocalDate targetWeekStart);
    Optional<PredictionRun> findFirstByModelVersion(String modelVersion);
    Optional<PredictionRun> findTopBySyntheticDataTrueOrderByTargetWeekStartDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PredictionRun r where r.targetWeekStart = :weekStart")
    Optional<PredictionRun> findByTargetWeekStartForUpdate(@Param("weekStart") LocalDate weekStart);

    Optional<PredictionRun> findTopBySyntheticDataFalseOrderByTargetWeekStartDesc();

    Optional<PredictionRun> findTopBySyntheticDataFalseAndTargetWeekStartLessThanOrderByTargetWeekStartDesc(LocalDate targetWeekStart);

    Optional<PredictionRun> findTopBySyntheticDataFalseAndTargetWeekStartGreaterThanOrderByTargetWeekStartAsc(LocalDate targetWeekStart);

    List<PredictionRun> findByConfirmedAtIsNullAndTargetWeekStartLessThanEqualOrderByTargetWeekStartAsc(
            LocalDate latestCompletedWeek);
}
