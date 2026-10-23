package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 상품 하나의 진행 중 할인. 특가는 remainingQuantity와 limitedQuantity가 null이다.
public record ActiveSale(
        Long productId,
        Long saleEventItemId,
        SaleEventType type,
        int salePrice,
        int discountRate,
        LocalDateTime endsAt,
        Integer remainingQuantity,
        Integer limitedQuantity
) {
}
