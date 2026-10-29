package org.example.takecareofmyfridgebe.domain.product.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;

@Builder
public record ProductListItemResponse(
        Long productId,
        ProductType productType,
        String name,
        String imageUrl,
        int price,
        ProductStatus status,
        // 진행 중 할인. 없으면 null
        SaleResponse sale,
        // 지금 보이는 가격으로 살 수 없으면 true. 상품 품절과 번개 드롭 남은 수량 0을 모두 포함한다
        boolean soldOut
) {
    public static ProductListItemResponse from(Product product, SellingPrice price) {
        return ProductListItemResponse.builder()
                .productId(product.getId())
                .productType(product.getProductType())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .status(product.getStatus())
                .sale(price.sale().map(SaleResponse::from).orElse(null))
                .soldOut(price.soldOut())
                .build();
    }
}
