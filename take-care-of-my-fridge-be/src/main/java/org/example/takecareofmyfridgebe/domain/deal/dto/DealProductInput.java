package org.example.takecareofmyfridgebe.domain.deal.dto;

import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;

public record DealProductInput(Long productId, String productName, int regularPrice,
                               ProductStatus status, int currentStock) {
}
