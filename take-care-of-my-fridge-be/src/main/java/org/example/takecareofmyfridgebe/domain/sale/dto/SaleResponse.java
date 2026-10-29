package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.time.OffsetDateTime;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 화면에 보여 줄 진행 중 할인. 상품 목록·상세가 같은 모양으로 내려 준다.
public record SaleResponse(
        SaleEventType type,
        int salePrice,
        int discountRate,
        // 종료 시각(+09:00 포함). 프론트가 서버 시각 기준으로 남은 시간을 계산한다.
        OffsetDateTime endsAt,
        // 번개 드롭 남은 수량·한정 수량. 특가는 null
        Integer remainingQuantity,
        Integer limitedQuantity
) {

    public static SaleResponse from(ActiveSale sale) {
        return new SaleResponse(sale.type(), sale.salePrice(), sale.discountRate(),
                sale.endsAt().atZone(SaleEvent.ZONE).toOffsetDateTime(),
                sale.remainingQuantity(), sale.limitedQuantity());
    }
}
