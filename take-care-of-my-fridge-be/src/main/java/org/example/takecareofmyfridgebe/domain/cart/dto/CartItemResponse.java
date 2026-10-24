package org.example.takecareofmyfridgebe.domain.cart.dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
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

    // sale은 진행 중인 할인이고 없으면 null이다. 번개 드롭은 남은 수량이 0이면 그 항목만 품절로 본다.
    public static CartItemResponse from(CartItem item, Product product, ActiveSale sale) {
        boolean flashDrop = sale != null && sale.type() == SaleEventType.FLASH_DROP;
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .quantity(item.getQuantity())
                .listPrice(product.getPrice())
                .unitPrice(sale == null ? product.getPrice() : sale.salePrice())
                .saleType(sale == null ? null : sale.type())
                .discountRate(sale == null ? null : sale.discountRate())
                .flashDropRemaining(flashDrop ? sale.remainingQuantity() : null)
                .flashDropLimit(flashDrop ? sale.limitedQuantity() : null)
                .saleEndsAt(sale == null ? null : sale.endsAt().atZone(SaleEvent.ZONE).toOffsetDateTime())
                .soldOut(isUnavailable(product) || (flashDrop && sale.remainingQuantity() == 0))
                .build();
    }

    // 판매 중지 상품도 품절과 같이 체크·주문에서 빠지도록 soldOut으로 표시한다
    private static boolean isUnavailable(Product product) {
        return product.getStock() == 0 || product.getStatus() == ProductStatus.STOPPED;
    }
}
