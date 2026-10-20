package org.example.takecareofmyfridgebe.domain.cart.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

@Builder
public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        int quantity,
        int listPrice,
        int unitPrice,
        SaleEventType saleType,
        Integer discountRate,
        Integer flashDropRemaining,
        boolean soldOut
) {

    // TODO: 할인 행사 연동 시 할인가·할인 구분·할인율·번개 드롭 남은 수량을 채운다
    public static CartItemResponse from(CartItem item, Product product) {
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .productName(product.getName())
                .quantity(item.getQuantity())
                .listPrice(product.getPrice())
                .unitPrice(product.getPrice())
                .soldOut(isUnavailable(product))
                .build();
    }

    // 판매 중지 상품도 품절과 같이 체크·주문에서 빠지도록 soldOut으로 표시한다
    private static boolean isUnavailable(Product product) {
        return product.getStock() == 0 || product.getStatus() == ProductStatus.STOPPED;
    }
}
