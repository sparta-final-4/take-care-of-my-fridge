package org.example.takecareofmyfridgebe.domain.forecast.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "forecast_batch_runs", uniqueConstraints =
        @UniqueConstraint(name = "uk_forecast_batch_runs_target_week_start", columnNames = "target_week_start"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForecastBatchRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_week_start", nullable = false)
    private LocalDate targetWeekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ForecastBatchStatus status;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "failure_type", length = 100)
    private String failureType;

    public ForecastBatchRun(LocalDate targetWeekStart, ForecastBatchStatus status,
                            LocalDateTime attemptedAt, String failureType) {
        this.targetWeekStart = targetWeekStart;
        update(status, attemptedAt, failureType);
    }

    public void update(ForecastBatchStatus status, LocalDateTime attemptedAt, String failureType) {
        this.status = status;
        this.attemptedAt = attemptedAt;
        this.failureType = failureType;
    }
}
