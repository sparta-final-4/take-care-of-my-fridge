package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 행사 목록의 한 행. 행사에 든 상품 하나가 한 행이고, 시각은 한국 시간이다.
// limitedQuantity, remainingQuantity는 번개 드롭만 값이 있다.
// soldQuantity는 결제 완료·구매 확정 주문으로 팔린 수량이다.
public record SaleEventListResponse(
        Long saleEventId,
        SaleEventType type,
        SaleEventStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long productId,
        String productName,
        int listPrice,
        int salePrice,
        int discountRate,
        Integer limitedQuantity,
        Integer remainingQuantity,
        long soldQuantity
) {
}
