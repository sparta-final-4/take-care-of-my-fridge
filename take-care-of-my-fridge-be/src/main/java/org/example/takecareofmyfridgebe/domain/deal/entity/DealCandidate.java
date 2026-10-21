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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

@Entity
@Table(name = "deal_candidates", uniqueConstraints =
        @UniqueConstraint(name = "uk_deal_candidates_analysis_product",
                columnNames = {"deal_analysis_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealCandidate extends BaseTimeEntity {

    public enum ExcludeReason { TOP_SELLER_ON_DAY, LOW_STOCK }
    public enum ReviewStatus { UNREVIEWED, SELECTED, HELD }
    public enum EventRequestStatus { NONE, REQUESTED, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_analysis_id", nullable = false)
    private DealAnalysis dealAnalysis;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "regular_price", nullable = false)
    private int regularPrice;

    @Column(name = "discount_price", nullable = false)
    private int discountPrice;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "avg_sales_on_day", nullable = false)
    private int avgSalesOnDay;

    @Column(name = "search_clicks", nullable = false)
    private int searchClicks;

    @Column(name = "purchases", nullable = false)
    private int purchases;

    @Column(name = "is_excluded", nullable = false)
    private boolean excluded;

    @Enumerated(EnumType.STRING)
    @Column(name = "exclude_reason")
    private ExcludeReason excludeReason;

    @Column(name = "candidate_reason", length = 255)
    private String candidateReason;

    @Column(name = "expected_qty_regular")
    private Integer expectedQtyRegular;

    @Column(name = "expected_qty_discount")
    private Integer expectedQtyDiscount;

    @Column(name = "expected_increase")
    private Integer expectedIncrease;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status")
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_request_status", nullable = false)
    private EventRequestStatus eventRequestStatus;

    @Column(name = "event_requested_at")
    private LocalDateTime eventRequestedAt;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Builder
    private DealCandidate(DealAnalysis dealAnalysis, Long productId, String productName,
                          int regularPrice, int discountPrice, int currentStock,
                          int avgSalesOnDay, int searchClicks, int purchases,
                          boolean excluded, ExcludeReason excludeReason, String candidateReason,
                          Integer expectedQtyRegular, Integer expectedQtyDiscount,
                          Integer expectedIncrease) {
        this.dealAnalysis = dealAnalysis;
        this.productId = productId;
        this.productName = productName;
        this.regularPrice = regularPrice;
        this.discountPrice = discountPrice;
        this.currentStock = currentStock;
        this.avgSalesOnDay = avgSalesOnDay;
        this.searchClicks = searchClicks;
        this.purchases = purchases;
        this.excluded = excluded;
        this.excludeReason = excludeReason;
        this.candidateReason = candidateReason;
        this.expectedQtyRegular = expectedQtyRegular;
        this.expectedQtyDiscount = expectedQtyDiscount;
        this.expectedIncrease = expectedIncrease;
        this.reviewStatus = excluded ? null : ReviewStatus.UNREVIEWED;
        this.eventRequestStatus = EventRequestStatus.NONE;
    }

    public void review(ReviewStatus status) {
        if (excluded) {
            throw new BusinessException(ErrorCode.DEAL_CANDIDATE_NOT_IN_ANALYSIS);
        }
        if (eventRequestStatus != EventRequestStatus.NONE) {
            throw new BusinessException(ErrorCode.DEAL_REVIEW_LOCKED);
        }
        if (status == null || status == ReviewStatus.UNREVIEWED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        this.reviewStatus = status;
    }
}
