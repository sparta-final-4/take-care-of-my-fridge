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

// DDL에 created_at/updated_at이 없어 BaseTimeEntity를 상속하지 않음
@Entity
@Table(
    name = "mealkit_products",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mealkit_products_product_id", columnNames = "product_id")
    },
    indexes = {
        @Index(name = "idx_mealkit_products_recipe_id", columnList = "recipe_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealkitProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 구성 인분
    @Column(name = "servings", nullable = false)
    private int servings;

    // 이 밀키트로 만드는 레시피. 상담 밀키트 추천이 레시피 조건으로 고를 때 쓴다 (없으면 null)
    // 타 도메인(Recipe)과의 결합도를 낮추기 위해 연관관계 대신 ID로만 참조
    @Column(name = "recipe_id", nullable = true)
    private Long recipeId;

    @Builder
    private MealkitProduct(Product product, int servings, Long recipeId) {
        this.product = product;
        this.servings = servings;
        this.recipeId = recipeId;
    }

    public void changeServings(int servings) {
        this.servings = servings;
    }
}
