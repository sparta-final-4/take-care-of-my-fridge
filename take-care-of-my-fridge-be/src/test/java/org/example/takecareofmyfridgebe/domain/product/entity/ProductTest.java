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
        return product(stock, ProductStatus.ON_SALE);
    }

    private Product product(int stock, ProductStatus status) {
        return Product.builder()
            .productType(ProductType.INGREDIENT)
            .name("대파")
            .price(3000)
            .stock(stock)
            .storage(StorageType.REFRIGERATED)
            .shelfDays(7)
            .status(status)
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
        Product soldOut = productWithStock(0);
        Product lowStock = productWithStock(2);

        assertThatThrownBy(() -> soldOut.decreaseStock(1))
            .isInstanceOf(BusinessException.class)
            .hasMessage("품절 · 대파");
        assertThatThrownBy(() -> lowStock.decreaseStock(3))
            .isInstanceOf(BusinessException.class)
            .hasMessage("재고 부족 · 대파");
    }

    @Test
    @DisplayName("재고를 수량만큼 되돌린다")
    void increaseStock_addsQuantity() {
        Product product = productWithStock(2);

        product.increaseStock(3);

        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("되돌릴 수량이 0 이하면 거절하고 재고는 그대로다")
    void increaseStock_rejectsNonPositiveQuantity() {
        Product product = productWithStock(5);

        assertThatThrownBy(() -> product.increaseStock(0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> product.increaseStock(-3))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고가 0이 되면 품절로 바뀌고, 남아 있으면 판매 중 그대로다")
    void decreaseStock_marksSoldOutWhenStockRunsOut() {
        Product lastOne = productWithStock(3);
        Product remaining = productWithStock(5);

        lastOne.decreaseStock(3);
        remaining.decreaseStock(3);

        assertThat(lastOne.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(remaining.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("거절된 차감은 상태를 바꾸지 않는다")
    void decreaseStock_keepsStatusWhenRejected() {
        Product product = productWithStock(2);

        assertThatThrownBy(() -> product.decreaseStock(3)).isInstanceOf(BusinessException.class);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("품절 상품의 재고를 되돌리면 판매 중으로 바뀐다")
    void increaseStock_marksOnSaleWhenSoldOut() {
        Product product = productWithStock(1);
        product.decreaseStock(1);

        product.increaseStock(2);

        assertThat(product.getStock()).isEqualTo(2);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("판매 중지 상품은 재고가 0이 되거나 되돌아와도 판매 중지 그대로다")
    void stockChange_keepsStopped() {
        Product product = product(1, ProductStatus.STOPPED);

        product.decreaseStock(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);

        product.increaseStock(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }
}
