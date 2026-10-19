package org.example.takecareofmyfridgebe.domain.recipe.entity;

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
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Table(
    name = "recipe_ingredients",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipe_ingredients_recipe_id_seq", columnNames = {"recipe_id", "seq"})
    },
    indexes = {
        @Index(name = "idx_recipe_ingredients_ingredient_id", columnList = "ingredient_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    // 타 도메인(Ingredient)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    // 레시피 내 순서 (1부터)
    @Column(name = "seq", nullable = false)
    private int seq;

    // 레시피에 적힌 재료명 그대로 (예: 다진 마늘)
    @Column(name = "display_name", length = 100)
    private String displayName;

    // 원문 용량 (1/2큰술, 약간, 100g(3개)…)
    @Column(name = "amount", length = 100)
    private String amount;

    // 수량 (재료 기본 단위 기준)
    @Column(name = "quantity", precision = 10, scale = 2)
    private BigDecimal quantity;

    // 재료 역할 (주재료/부재료/양념)
    @Column(name = "role", length = 20)
    private String role;

    // 원문 그룹명 (양념장, 고명…)
    @Column(name = "group_name", length = 50)
    private String groupName;

    // 파싱 전 항목 원문
    @Column(name = "raw_text", length = 255)
    private String rawText;

    @Builder
    private RecipeIngredient(
        Recipe recipe,
        Long ingredientId,
        int seq,
        String displayName,
        String amount,
        BigDecimal quantity,
        String role,
        String groupName,
        String rawText
    ) {
        this.recipe = recipe;
        this.ingredientId = ingredientId;
        this.seq = seq;
        this.displayName = displayName;
        this.amount = amount;
        this.quantity = quantity;
        this.role = role;
        this.groupName = groupName;
        this.rawText = rawText;
    }
}
