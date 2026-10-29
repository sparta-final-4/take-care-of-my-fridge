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

    public boolean isFlashDrop() {
        return type == SaleEventType.FLASH_DROP;
    }

    // 번개 드롭 남은 수량이 0이면 상품 재고가 남아 있어도 할인가로는 더 팔지 않는다
    public boolean isExhausted() {
        return isFlashDrop() && remainingQuantity == 0;
    }
}
