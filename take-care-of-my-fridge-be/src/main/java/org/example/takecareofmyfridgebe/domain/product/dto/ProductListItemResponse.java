package org.example.takecareofmyfridgebe.domain.product.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleInfoResponse;

@Builder
public record ProductListItemResponse(
        Long productId,
        ProductType productType,
        String name,
        String imageUrl,
        int price,
        ProductStatus status,
        // 진행 중인 할인 정보. 할인이 없으면 null
        SaleInfoResponse sale
) {
    public static ProductListItemResponse from(Product product) {
        return ProductListItemResponse.builder()
                .productId(product.getId())
                .productType(product.getProductType())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .status(product.getStatus())
                .build();
    }

    public ProductListItemResponse withSale(SaleInfoResponse sale) {
        return new ProductListItemResponse(productId, productType, name, imageUrl, price, status, sale);
    }
}
