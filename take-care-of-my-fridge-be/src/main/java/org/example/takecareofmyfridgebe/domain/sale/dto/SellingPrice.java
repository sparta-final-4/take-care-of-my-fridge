package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 상품 하나를 지금 얼마에 파는지, 품절인지 답하는 값 객체. ID 없이 조회할 때 만들고, 만든 뒤 바꾸지 않는다.
// 단가·품절·가격 확인 규칙을 여기 모아 상품 화면, 장바구니, 주문하기가 같은 판단을 쓰게 한다.
// 할인이 없으면 sale이 null이고, 그 분기는 이 클래스 안에서만 한다.
public final class SellingPrice {

    private final int listPrice;
    private final boolean productSoldOut;
    private final ActiveSale sale;

    private SellingPrice(int listPrice, boolean productSoldOut, ActiveSale sale) {
        this.listPrice = listPrice;
        this.productSoldOut = productSoldOut;
        this.sale = sale;
    }

    // sale은 진행 중인 할인이고 없으면 null이다.
    // 상품 품절은 상태로 판단한다. 판매 중지도 살 수 없으니 품절로 본다.
    public static SellingPrice of(Product product, ActiveSale sale) {
        return new SellingPrice(product.getPrice(), product.getStatus() != ProductStatus.ON_SALE, sale);
    }

    public int listPrice() {
        return listPrice;
    }

    public int unitPrice() {
        return sale == null ? listPrice : sale.salePrice();
    }

    // 할인이 없으면 null
    public SaleEventType saleType() {
        return sale == null ? null : sale.type();
    }

    // 할인이 없으면 null
    public Long saleEventItemId() {
        return sale == null ? null : sale.saleEventItemId();
    }

    public boolean isFlashDrop() {
        return sale != null && sale.isFlashDrop();
    }

    public Optional<ActiveSale> sale() {
        return Optional.ofNullable(sale);
    }

    // 지금 보이는 가격으로 살 수 없으면 품절이다. 상품 품절과 번개 드롭이 다 팔린 경우를 화면은 구분하지 않는다.
    // 할인 중이라고 할인 쪽 수량만 보지 않는다. 특가는 한정 수량이 없고, 번개 드롭 중에도 상품 재고가 남은 수량보다 먼저 0이 될 수 있다.
    public boolean soldOut() {
        return productSoldOut || (sale != null && sale.isExhausted());
    }

    // 사용자가 본 단가·할인 구분이 지금과 같은지. 오르든 내리든 다르면 false다.
    public boolean matches(int requestedUnitPrice, SaleEventType requestedSaleType) {
        return unitPrice() == requestedUnitPrice && saleType() == requestedSaleType;
    }
}
