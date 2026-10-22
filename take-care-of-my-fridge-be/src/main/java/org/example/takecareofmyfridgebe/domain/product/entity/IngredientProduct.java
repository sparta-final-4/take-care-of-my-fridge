package org.example.takecareofmyfridgebe.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DDL에 created_at/updated_at이 없어 BaseTimeEntity를 상속하지 않음
@Entity
@Table(
    name = "ingredient_products",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ingredient_products_product_id", columnNames = "product_id")
    },
    indexes = {
        @Index(name = "idx_ingredient_products_ingredient_id", columnList = "ingredient_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 타 도메인(Ingredient)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    // 용량 수치 (예: 300)
    @Column(name = "unit_amount", precision = 8, scale = 2, nullable = false)
    private BigDecimal unitAmount;

    // 용량 단위 (g, ml, 개, 팩…)
    @Column(name = "unit", length = 20, nullable = false)
    private String unit;

    // 상품 1개가 재료 기본 단위로 몇 개인지
    @Column(name = "base_quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseQuantity;

    @Builder
    private IngredientProduct(
        Product product,
        Long ingredientId,
        BigDecimal unitAmount,
        String unit,
        BigDecimal baseQuantity
    ) {
        this.product = product;
        this.ingredientId = ingredientId;
        this.unitAmount = unitAmount;
        this.unit = unit;
        this.baseQuantity = baseQuantity;
    }

    public void update(Long ingredientId, BigDecimal unitAmount, String unit, BigDecimal baseQuantity) {
        this.ingredientId = ingredientId;
        this.unitAmount = unitAmount;
        this.unit = unit;
        this.baseQuantity = baseQuantity;
    }
}
