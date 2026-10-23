package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 상품 목록·상세 응답의 `sale`에 담는 진행 중 할인 정보. 진행 중인 행사만 담으므로 시작 시각은 두지 않는다.
// 특가는 remainingQuantity가 null이다.
public record SaleInfoResponse(
        SaleEventType type,
        int salePrice,
        int discountRate,
        LocalDateTime endsAt,
        Integer remainingQuantity,
        Long saleEventItemId
) {
    public static SaleInfoResponse from(ActiveSale sale) {
        return new SaleInfoResponse(sale.type(), sale.salePrice(), sale.discountRate(), sale.endsAt(),
                sale.remainingQuantity(), sale.saleEventItemId());
    }
}
