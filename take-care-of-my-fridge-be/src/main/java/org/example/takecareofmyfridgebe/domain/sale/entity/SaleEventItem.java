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
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

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

    // 등록 당시 정가. 종료된 행사 목록에서 그 당시 정가를 보여 주려고 저장한다.
    @Column(name = "list_price", nullable = false)
    private int listPrice;

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

    // 할인가는 정가에서 할인율만큼 뺀 값이며 원 아래는 버린다.
    @Builder
    private SaleEventItem(
        SaleEvent saleEvent,
        Long productId,
        int listPrice,
        int discountRate,
        Integer limitedQuantity
    ) {
        this.saleEvent = saleEvent;
        this.productId = productId;
        this.listPrice = listPrice;
        this.salePrice = calculateSalePrice(listPrice, discountRate);
        this.discountRate = discountRate;
        this.limitedQuantity = limitedQuantity;
        this.remainingQuantity = limitedQuantity;
    }

    // 시작 전 행사의 정가·할인율·한정 수량을 바꾼다. 아직 팔리지 않았으므로 남은 수량도 한정 수량으로 맞춘다.
    public void update(int listPrice, int discountRate, Integer limitedQuantity) {
        this.listPrice = listPrice;
        this.salePrice = calculateSalePrice(listPrice, discountRate);
        this.discountRate = discountRate;
        this.limitedQuantity = limitedQuantity;
        this.remainingQuantity = limitedQuantity;
    }

    private static int calculateSalePrice(int listPrice, int discountRate) {
        return (int) ((long) listPrice * (100 - discountRate) / 100);
    }

    // 번개 드롭 남은 수량을 줄인다. 모자라면 빼지 않고 거절한다.
    public void decreaseRemaining(int quantity) {
        if (remainingQuantity == null || remainingQuantity < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "품절 · 번개 드롭");
        }
        this.remainingQuantity -= quantity;
    }

    // 주문 취소로 남은 수량을 되돌린다. 한정 수량을 넘지 않는다.
    public void increaseRemaining(int quantity) {
        if (remainingQuantity == null) {
            return;
        }
        this.remainingQuantity = Math.min(limitedQuantity, remainingQuantity + quantity);
    }
}
