package org.example.takecareofmyfridgebe.domain.sale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "sale_events",
    indexes = {
        @Index(name = "idx_sale_events_status_starts_at", columnList = "status, starts_at"),
        @Index(name = "idx_sale_events_status_ends_at", columnList = "status, ends_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleEvent extends BaseTimeEntity {

    // 행사 시각은 한국 시간이다. 서버 시간대와 관계없이 같은 기준으로 비교한다.
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SaleEventType type;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SaleEventStatus status;

    @Builder
    private SaleEvent(SaleEventType type, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.type = type;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = SaleEventStatus.SCHEDULED;
    }
}
