package org.example.takecareofmyfridgebe.domain.forecast.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "sales_forecasts", uniqueConstraints =
        @UniqueConstraint(name = "uk_sales_forecasts_run_product",
                columnNames = {"prediction_run_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesForecast extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediction_run_id", nullable = false)
    private PredictionRun predictionRun;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "predicted_rank")
    private Integer predictedRank;

    @Column(name = "predicted_qty")
    private Integer predictedQty;

    @Column(name = "baseline_qty", nullable = false)
    private int baselineQty;

    @Column(name = "actual_rank")
    private Integer actualRank;

    @Column(name = "actual_qty")
    private Integer actualQty;

    @Builder
    private SalesForecast(PredictionRun predictionRun, Long productId, String productName,
                          Integer predictedRank, Integer predictedQty, int baselineQty,
                          Integer actualRank, Integer actualQty) {
        this.predictionRun = predictionRun;
        this.productId = productId;
        this.productName = productName;
        this.predictedRank = predictedRank;
        this.predictedQty = predictedQty;
        this.baselineQty = baselineQty;
        this.actualRank = actualRank;
        this.actualQty = actualQty;
    }

    public void confirmActual(Integer rank, int quantity) {
        if (actualQty != null) {
            throw new IllegalStateException("이미 실적이 확정된 상품입니다.");
        }
        this.actualRank = rank;
        this.actualQty = quantity;
    }
}
