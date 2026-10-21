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
        ),
        @UniqueConstraint(
            name = "uk_orders_member_id_idempotency_key",
            columnNames = {"member_id", "idempotency_key"}
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

    // 주문하기 클릭마다 프론트가 만든 값. 같은 값으로 다시 오면 새 주문을 만들지 않는다 (기존 주문은 null)
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    // 결제 완료 일시 (구매 확정 스케줄러 기준, 결제 전에는 null)
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 구매 확정 일시 (확정 전에는 null)
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Builder
    private Order(String orderNumber, Long memberId, String idempotencyKey, long totalAmount) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.idempotencyKey = idempotencyKey;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
    }

    // 결제 대기 주문인지 확인한다. 아니면 왜 아닌지(취소, 결제 완료)를 나눠 알려 준다.
    public void validatePending() {
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED);
        }
        if (status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
        }
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

    public void confirm(LocalDateTime confirmedAt) {
        if (status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }
}
