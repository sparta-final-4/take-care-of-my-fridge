package org.example.takecareofmyfridgebe.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Builder
    private MealkitProduct(Product product, int servings) {
        this.product = product;
        this.servings = servings;
    }
}
