package org.example.takecareofmyfridgebe.domain.fridge.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fridge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fridge extends BaseTimeEntity {

    // 잔여 수량 0 처리 기준값 (0.05 미만)
    private static final BigDecimal ZERO_THRESHOLD = new BigDecimal("0.05");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 타 도메인(Member)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "order_item_id", nullable = true)
    private Long orderItemId;

    @Column(name = "raw_name", length = 200, nullable = false)
    private String rawName;

    @Column(name = "quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage", nullable = false)
    private StorageType storage;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private FridgeSource source;

    @Builder
    private Fridge(
        Long memberId,
        Ingredient ingredient,
        Long orderItemId,
        String rawName,
        BigDecimal quantity,
        StorageType storage,
        LocalDate expirationDate,
        FridgeSource source
    ) {
        FridgeSource resolvedSource = source != null ? source : FridgeSource.MANUAL;
        validatePositiveQuantity(quantity);
        validateOrderItem(resolvedSource, orderItemId);

        this.memberId = memberId;
        this.ingredient = ingredient;
        this.orderItemId = orderItemId;
        this.rawName = rawName;
        this.quantity = quantity;
        this.storage = storage;
        this.expirationDate = expirationDate;
        this.source = resolvedSource;
    }

    // 재료 정보(표준 재료, 표시명, 수량, 보관방식, 소비기한) 부분 수정. null인 칸은 바꾸지 않는다.
    public void update(
        Ingredient ingredient,
        String rawName,
        BigDecimal quantity,
        StorageType storage,
        LocalDate expirationDate
    ) {
        if (quantity != null) {
            validatePositiveQuantity(quantity);
            this.quantity = quantity;
        }
        if (ingredient != null) {
            this.ingredient = ingredient;
        }
        if (rawName != null) {
            this.rawName = rawName;
        }
        if (storage != null) {
            this.storage = storage;
        }
        if (expirationDate != null) {
            this.expirationDate = expirationDate;
        }
    }

    // 재료 수량 차감 (잔여량이 0.05 미만이면 0으로 보정, 음수 방지)
    public void deduct(BigDecimal amount) {
        validatePositiveQuantity(amount);
        BigDecimal remaining = this.quantity.subtract(amount);
        if (remaining.compareTo(ZERO_THRESHOLD) < 0) {
            this.quantity = BigDecimal.ZERO;
        } else {
            this.quantity = remaining;
        }
    }

    // 잔여 수량 모두 소진(0) 여부 확인
    public boolean isEmpty() {
        return this.quantity.compareTo(BigDecimal.ZERO) == 0;
    }

    // 수량은 0보다 커야 함
    private static void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_FRIDGE_QUANTITY);
        }
    }

    // 커머스 입고만 주문 상품 ID를 가짐
    private static void validateOrderItem(FridgeSource source, Long orderItemId) {
        boolean isCommerce = source == FridgeSource.COMMERCE;
        boolean hasOrderItem = orderItemId != null;

        if (isCommerce != hasOrderItem) {
            throw new BusinessException(ErrorCode.INVALID_FRIDGE_ORDER_ITEM);
        }
    }
}
