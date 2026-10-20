package org.example.takecareofmyfridgebe.domain.deal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "deal_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealAnalysis extends BaseTimeEntity {

    public enum Day {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "target_week_start", nullable = false)
    private LocalDate targetWeekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 3)
    private Day dayOfWeek;

    @Column(name = "discount_rate", nullable = false)
    private int discountRate;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "data_cutoff_at", nullable = false)
    private LocalDateTime dataCutoffAt;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Builder
    private DealAnalysis(Member member, LocalDate targetWeekStart, Day dayOfWeek,
                         int discountRate, int minStock, String modelVersion,
                         LocalDateTime dataCutoffAt) {
        this.member = member;
        this.targetWeekStart = targetWeekStart;
        this.dayOfWeek = dayOfWeek;
        this.discountRate = discountRate;
        this.minStock = minStock;
        this.modelVersion = modelVersion;
        this.dataCutoffAt = dataCutoffAt;
    }

    public void markReviewsSaved(LocalDateTime savedAt) {
        this.lastSavedAt = savedAt;
    }
}
