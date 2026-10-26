package org.example.takecareofmyfridgebe.domain.refund.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

// 환불 한 건에 포함된 주문 상품. 단가는 주문 당시 값을 복사하고 금액은 단가 × 수량으로 계산한다
@Entity
@Table(name = "refund_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_items_refund_id_order_item_id",
                columnNames = {"refund_id", "order_item_id"}),
        indexes = @Index(name = "idx_refund_items_order_item_id", columnList = "order_item_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(nullable = false)
    private int quantity;

    // 주문 당시 단가 (주문 상품 스냅샷)
    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private Long amount;

    @Builder
    private RefundItem(Refund refund, OrderItem orderItem, int quantity) {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_QUANTITY);
        }
        this.refund = refund;
        this.orderItem = orderItem;
        this.quantity = quantity;
        this.unitPrice = orderItem.getUnitPrice();
        this.amount = (long) unitPrice * quantity;
    }
}
