package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;

// 특가·번개 드롭은 고른 상품마다 행사가 하나씩 만들어져, 만든 행사 번호를 상품 순서대로 돌려준다.
public record SaleEventsCreateResponse(
        List<Long> saleEventIds
) {
    public static SaleEventsCreateResponse from(List<SaleEvent> saleEvents) {
        return new SaleEventsCreateResponse(saleEvents.stream().map(SaleEvent::getId).toList());
    }
}
