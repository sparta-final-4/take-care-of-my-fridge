package org.example.takecareofmyfridgebe.domain.cart.dto;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

@Builder
public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String imageUrl,
        int quantity,
        int listPrice,
        int unitPrice,
        SaleEventType saleType,
        Integer discountRate,
        Integer flashDropRemaining,
        // 번개 드롭 한정 수량(총 판매 수량). 번개 드롭이 아니면 null
        Integer flashDropLimit,
        // 진행 중 할인의 종료 시각(+09:00 포함). 프론트가 서버 시각 기준으로 남은 시간을 계산한다. 할인이 없으면 null
        OffsetDateTime saleEndsAt,
        boolean soldOut
) {

    // 판단은 price가 끝낸 상태로 받는다.
    public static CartItemResponse from(CartItem item, Product product, SellingPrice price) {
        Optional<ActiveSale> flashDrop = price.sale().filter(ActiveSale::isFlashDrop);
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .quantity(item.getQuantity())
                .listPrice(price.listPrice())
                .unitPrice(price.unitPrice())
                .saleType(price.saleType())
                .discountRate(price.sale().map(ActiveSale::discountRate).orElse(null))
                .flashDropRemaining(flashDrop.map(ActiveSale::remainingQuantity).orElse(null))
                .flashDropLimit(flashDrop.map(ActiveSale::limitedQuantity).orElse(null))
                .saleEndsAt(price.sale().map(active -> active.endsAt().atZone(SaleEvent.ZONE).toOffsetDateTime()).orElse(null))
                .soldOut(price.soldOut())
                .build();
    }
}
