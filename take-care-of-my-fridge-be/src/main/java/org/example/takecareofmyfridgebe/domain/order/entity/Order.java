package org.example.takecareofmyfridgebe.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "orders",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_orders_order_number",
            columnNames = "order_number"
        )
    },
    indexes = {
        @Index(name = "idx_orders_member_id_status", columnList = "member_id, status"),
        @Index(name = "idx_orders_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_orders_status_paid_at", columnList = "status, paid_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 날짜 + 일련번호 (예: 20261013-000123)
    @Column(name = "order_number", nullable = false, length = 20)
    private String orderNumber;

    // 타 도메인(Member)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    // 결제 완료 일시 (구매 확정 스케줄러 기준, 결제 전에는 null)
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Builder
    private Order(String orderNumber, Long memberId, long totalAmount) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
    }

    public void markPaid(LocalDateTime paidAt) {
        if (status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.PAID;
        this.paidAt = paidAt;
    }

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void confirm() {
        if (status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CONFIRMED;
    }
}
