package org.example.takecareofmyfridgebe.domain.order.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(
    name = "order_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_order_items_sale_event_item_id_flash_drop_member_id",
            columnNames = {"sale_event_item_id", "flash_drop_member_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 타 도메인(Product)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    // 주문 당시 정가
    @Column(name = "list_price", nullable = false)
    private int listPrice;

    // 실제 단가 (할인가 또는 정가)
    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_type", nullable = false)
    private SaleType saleType;

    // 특가·번개 드롭으로 샀을 때만 값이 있고 일반 상품은 null
    @Column(name = "sale_event_item_id")
    private Long saleEventItemId;

    // 번개 드롭 1인 1개 확인용 (번개 드롭 줄만, 취소하면 null)
    @Column(name = "flash_drop_member_id")
    private Long flashDropMemberId;

    @Builder
    private OrderItem(
        Order order,
        Long productId,
        String productName,
        int quantity,
        int listPrice,
        int unitPrice,
        SaleType saleType,
        Long saleEventItemId,
        Long flashDropMemberId
    ) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.listPrice = listPrice;
        this.unitPrice = unitPrice;
        this.saleType = saleType;
        this.saleEventItemId = saleEventItemId;
        this.flashDropMemberId = flashDropMemberId;
    }

    // 주문 취소 시 번개 드롭 1인 1개 기록을 비움
    public void releaseFlashDropMember() {
        this.flashDropMemberId = null;
    }
}
