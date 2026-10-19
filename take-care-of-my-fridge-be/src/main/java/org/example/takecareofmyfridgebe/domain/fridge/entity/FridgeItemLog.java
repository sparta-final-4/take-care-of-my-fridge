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
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.math.BigDecimal;

@Entity
@Table(name = "fridge_item_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeItemLog extends BaseTimeEntity {

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

    // 타 도메인(CookingLog)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조 (요리 완료로 소진한 경우만)
    @Column(name = "cooking_log_id", nullable = true)
    private Long cookingLogId;

    // 원본 Fridge 행 삭제 후에도 이력 유지를 위해 FK 없이 ID만 보관
    @Column(name = "fridge_id", nullable = false)
    private Long fridgeId;

    @Column(name = "raw_name", length = 200, nullable = false)
    private String rawName;

    @Column(name = "quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private FridgeItemAction action;

    @Builder
    private FridgeItemLog(
        Long memberId,
        Ingredient ingredient,
        Long cookingLogId,
        Long fridgeId,
        String rawName,
        BigDecimal quantity,
        FridgeItemAction action
    ) {
        validateCookingLog(action, cookingLogId);

        this.memberId = memberId;
        this.ingredient = ingredient;
        this.cookingLogId = cookingLogId;
        this.fridgeId = fridgeId;
        this.rawName = rawName;
        this.quantity = quantity;
        this.action = action;
    }

    // 버린 재료에는 요리 기록을 연결할 수 없음
    private static void validateCookingLog(FridgeItemAction action, Long cookingLogId) {
        if (action == FridgeItemAction.DISCARDED && cookingLogId != null) {
            throw new BusinessException(ErrorCode.INVALID_FRIDGE_LOG);
        }
    }
}
