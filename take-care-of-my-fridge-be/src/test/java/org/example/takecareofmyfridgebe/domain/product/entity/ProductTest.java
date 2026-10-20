package org.example.takecareofmyfridgebe.domain.product.entity;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product productWithStock(int stock) {
        return Product.builder()
            .productType(ProductType.INGREDIENT)
            .name("대파")
            .price(3000)
            .stock(stock)
            .storage(StorageType.REFRIGERATED)
            .shelfDays(7)
            .build();
    }

    @Test
    @DisplayName("재고를 수량만큼 뺀다. 재고와 같은 수량이면 0이 된다")
    void decreaseStock_subtractsQuantity() {
        Product product = productWithStock(3);

        product.decreaseStock(3);

        assertThat(product.getStock()).isZero();
    }

    @Test
    @DisplayName("재고보다 많이 빼려 하면 거절하고 재고는 그대로다")
    void decreaseStock_rejectsWhenShort() {
        Product product = productWithStock(2);

        assertThatThrownBy(() -> product.decreaseStock(3))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("0 이하의 수량은 거절하고 재고는 그대로다")
    void decreaseStock_rejectsNonPositiveQuantity() {
        Product product = productWithStock(5);

        assertThatThrownBy(() -> product.decreaseStock(0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> product.decreaseStock(-3))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고가 0이면 품절, 모자라면 재고 부족 문구에 상품명을 담는다")
    void decreaseStock_messageIncludesName() {
        assertThatThrownBy(() -> productWithStock(0).decreaseStock(1))
            .isInstanceOf(BusinessException.class)
            .hasMessage("품절 · 대파");
        assertThatThrownBy(() -> productWithStock(2).decreaseStock(3))
            .isInstanceOf(BusinessException.class)
            .hasMessage("재고 부족 · 대파");
    }
}
