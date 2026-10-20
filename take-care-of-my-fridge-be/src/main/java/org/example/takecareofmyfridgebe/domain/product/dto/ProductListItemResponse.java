package org.example.takecareofmyfridgebe.domain.product.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

@Builder
public record ProductListItemResponse(
        Long productId,
        ProductType productType,
        String name,
        String imageUrl,
        int price,
        ProductStatus status
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
}
