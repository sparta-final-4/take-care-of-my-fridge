package org.example.takecareofmyfridgebe.domain.product.dto;

import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

// ES 상품 색인용. 재고는 주문마다 바뀌므로 색인하지 않는다
public record ProductIndexSource(
        Long id,
        ProductType productType,
        String name,
        String description,
        int price,
        ProductStatus status
) {
    public static ProductIndexSource from(Product product) {
        return new ProductIndexSource(
                product.getId(),
                product.getProductType(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus()
        );
    }
}
