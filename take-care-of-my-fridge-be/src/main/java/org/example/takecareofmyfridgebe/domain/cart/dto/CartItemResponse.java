package org.example.takecareofmyfridgebe.domain.cart.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;

@Builder
public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String imageUrl,
        int quantity,
        int listPrice,
        int unitPrice,
        // 진행 중인 할인. 상품 목록·상세와 같은 모양이다. 할인이 없으면 null.
        SaleResponse sale,
        boolean soldOut
) {

    // 판단은 price가 끝낸 상태로 받는다.
    public static CartItemResponse from(CartItem item, Product product, SellingPrice price) {
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .quantity(item.getQuantity())
                .listPrice(price.listPrice())
                .unitPrice(price.unitPrice())
                .sale(price.sale().map(SaleResponse::from).orElse(null))
                .soldOut(price.soldOut())
                .build();
    }
}
