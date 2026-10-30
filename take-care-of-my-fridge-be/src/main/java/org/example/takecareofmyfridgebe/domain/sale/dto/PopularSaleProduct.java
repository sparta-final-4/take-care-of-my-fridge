package org.example.takecareofmyfridgebe.domain.sale.dto;

import org.example.takecareofmyfridgebe.domain.product.entity.Product;

// 인기 특가로 고른 상품과 그 상품의 진행 중 할인
public record PopularSaleProduct(Product product, ActiveSale sale) {
}
