package org.example.takecareofmyfridgebe.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_status", columnList = "status")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 상품 종류 (INGREDIENT: 재료 / MEALKIT: 밀키트)
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 판매가(원)
    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage", nullable = false)
    private StorageType storage;

    // 권장 보관 일수 (구매 확정일 + 일수 = 냉장고 소비기한)
    @Column(name = "shelf_days", nullable = false)
    private int shelfDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Builder
    private Product(
        ProductType productType,
        String name,
        String description,
        String imageUrl,
        int price,
        int stock,
        StorageType storage,
        int shelfDays,
        ProductStatus status
    ) {
        this.productType = productType;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.stock = stock;
        this.storage = storage;
        this.shelfDays = shelfDays;
        this.status = status != null ? status : ProductStatus.ON_SALE;
    }
}
