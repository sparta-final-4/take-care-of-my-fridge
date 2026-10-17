package org.example.takecareofmyfridgebe.domain.sale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(
    name = "sale_event_items",
    indexes = {
        @Index(name = "idx_sale_event_items_product_id", columnList = "product_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleEventItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_event_id", nullable = false)
    private SaleEvent saleEvent;

    // 타 도메인(Product)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // 할인가 (번개 드롭은 번개 드롭가)
    @Column(name = "sale_price", nullable = false)
    private int salePrice;

    // 할인율(%, 정가 대비). 관리자가 입력하고 서버가 이 값으로 할인가를 계산해 저장
    @Column(name = "discount_rate", nullable = false)
    private int discountRate;

    // 한정 수량 (번개 드롭만, 특가는 null)
    @Column(name = "limited_quantity")
    private Integer limitedQuantity;

    // 남은 수량 (번개 드롭만, 특가는 null)
    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    @Builder
    private SaleEventItem(
        SaleEvent saleEvent,
        Long productId,
        int salePrice,
        int discountRate,
        Integer limitedQuantity
    ) {
        this.saleEvent = saleEvent;
        this.productId = productId;
        this.salePrice = salePrice;
        this.discountRate = discountRate;
        this.limitedQuantity = limitedQuantity;
        this.remainingQuantity = limitedQuantity;
    }
}
