package org.example.takecareofmyfridgebe.domain.fridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(
    name = "fridge_commerce_histories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_fridge_commerce_histories_order_item_id",
            columnNames = "order_item_id"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeCommerceHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 타 도메인(Member)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 주문상품 기준 중복 입고 방지 (DB 유니크 제약)
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Builder
    private FridgeCommerceHistory(Long memberId, Long orderItemId) {
        this.memberId = memberId;
        this.orderItemId = orderItemId;
    }
}
