package org.example.takecareofmyfridgebe.domain.sale.dto;

import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

// 행사 등록 화면의 상품 표 한 행. 공개 상품 목록에 없는 재고를 함께 준다.
public record SaleCandidateProductResponse(
        Long productId,
        ProductType productType,
        String name,
        String imageUrl,
        int price,
        int stock
) {
    public static SaleCandidateProductResponse from(Product product) {
        return new SaleCandidateProductResponse(product.getId(), product.getProductType(), product.getName(),
                product.getImageUrl(), product.getPrice(), product.getStock());
    }
}
