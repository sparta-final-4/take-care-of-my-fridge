package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 행사 수정 화면에 보여 줄 행사 하나와 그 상품들. 시각은 한국 시간이다.
// limitedQuantity, remainingQuantity는 번개 드롭만 값이 있다.
public record SaleEventDetailResponse(
        Long saleEventId,
        SaleEventType type,
        SaleEventStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<SaleEventDetailItem> items
) {
    // 이름을 `Item`으로 두면 생성 스펙에서 주문 상품의 `Item`과 겹쳐서 다르게 짓는다.
    // listPrice는 종료된 행사면 등록 당시 정가, 그 밖에는 상품의 현재 정가다(목록과 같다).
    public record SaleEventDetailItem(
            Long productId,
            String productName,
            int listPrice,
            int salePrice,
            int discountRate,
            Integer limitedQuantity,
            Integer remainingQuantity
    ) {
    }

    public static SaleEventDetailResponse from(
            SaleEvent saleEvent, List<SaleEventItem> items, Map<Long, Product> productsById) {
        return new SaleEventDetailResponse(
                saleEvent.getId(), saleEvent.getType(), saleEvent.getStatus(),
                saleEvent.getStartsAt(), saleEvent.getEndsAt(),
                items.stream()
                        .map(item -> toItem(saleEvent, item, productsById.get(item.getProductId())))
                        .toList());
    }

    private static SaleEventDetailItem toItem(SaleEvent saleEvent, SaleEventItem item, Product product) {
        int listPrice = saleEvent.getStatus() == SaleEventStatus.ENDED ? item.getListPrice() : product.getPrice();
        return new SaleEventDetailItem(product.getId(), product.getName(), listPrice, item.getSalePrice(), item.getDiscountRate(),
                item.getLimitedQuantity(), item.getRemainingQuantity());
    }
}
