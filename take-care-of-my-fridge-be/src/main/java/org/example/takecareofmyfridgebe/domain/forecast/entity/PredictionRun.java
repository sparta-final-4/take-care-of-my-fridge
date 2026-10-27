package org.example.takecareofmyfridgebe.domain.forecast.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "prediction_runs", uniqueConstraints =
        @UniqueConstraint(name = "uk_prediction_runs_target_week_start", columnNames = "target_week_start"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PredictionRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_week_start", nullable = false)
    private LocalDate targetWeekStart;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "data_cutoff_at", nullable = false)
    private LocalDateTime dataCutoffAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "synthetic_data", nullable = false)
    private boolean syntheticData;

    @Builder
    private PredictionRun(LocalDate targetWeekStart, String modelVersion,
                          LocalDateTime dataCutoffAt, LocalDateTime confirmedAt,
                          boolean syntheticData) {
        this.targetWeekStart = targetWeekStart;
        this.modelVersion = modelVersion;
        this.dataCutoffAt = dataCutoffAt;
        this.confirmedAt = confirmedAt;
        this.syntheticData = syntheticData;
    }

    public void confirm(LocalDateTime confirmedAt) {
        if (this.confirmedAt != null) {
            throw new IllegalStateException("이미 실적이 확정된 예측입니다.");
        }
        this.confirmedAt = confirmedAt;
    }
}
