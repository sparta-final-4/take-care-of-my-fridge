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
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

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

    // 재고는 음수가 될 수 없다. 모자라면 빼지 않고 거절하며, 화면에 띄울 문구(품절·재고 부족 + 상품명)를 함께 담는다.
    // 수량이 0 이하면 재고가 늘어나므로 먼저 거절한다.
    // 판매 중인 상품의 재고가 0이 되면 품절로 바꾼다. 판매 중지 상품의 상태는 건드리지 않는다.
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "수량은 1 이상이어야 합니다.");
        }
        if (stock == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "품절 · " + name);
        }
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "재고 부족 · " + name);
        }
        this.stock -= quantity;
        if (stock == 0 && status == ProductStatus.ON_SALE) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    // 주문 취소·환불로 재고를 되돌린다. 수량이 0 이하면 재고가 줄어들므로 거절한다.
    // 품절 상품은 재고가 1 이상이 되므로 판매 중으로 바꾼다. 판매 중지 상품의 상태는 건드리지 않는다.
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "수량은 1 이상이어야 합니다.");
        }
        this.stock += quantity;
        if (status == ProductStatus.SOLD_OUT) {
            this.status = ProductStatus.ON_SALE;
        }
    }

    // 상품 종류(productType)는 상세 테이블이 달라 바꾸지 않는다.
    public void changeInfo(String name, String description, String imageUrl) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    // 판매 중지가 아니면 판매 중·품절은 재고로 정한다(재고 0 = 품절). 요청한 상태가 재고와 어긋나도 재고를 따른다.
    public void changeSales(int price, int stock, ProductStatus status) {
        this.price = price;
        this.stock = stock;
        if (status == ProductStatus.STOPPED) {
            this.status = ProductStatus.STOPPED;
        } else {
            this.status = stock == 0 ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE;
        }
    }

    public void changeStorage(StorageType storage, int shelfDays) {
        this.storage = storage;
        this.shelfDays = shelfDays;
    }

    // 주문, 장바구니가 productId로 참조하므로 행을 지우지 않고 판매 중지(STOPPED)로 바꾼다.
    public void stop() {
        this.status = ProductStatus.STOPPED;
    }
}
