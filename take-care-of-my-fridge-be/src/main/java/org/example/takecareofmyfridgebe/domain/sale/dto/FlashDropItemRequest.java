package org.example.takecareofmyfridgebe.domain.sale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;

// 1인 구매 한도는 1개로 고정이라 받지 않는다.
public record FlashDropItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotNull(message = "할인율은 필수입니다.")
        @Min(value = 1, message = "할인율은 1% 이상이어야 합니다.")
        @Max(value = 99, message = "할인율은 99% 이하여야 합니다.")
        Integer discountRate,

        @NotNull(message = "한정 수량은 필수입니다.")
        @Min(value = 1, message = "한정 수량은 1개 이상이어야 합니다.")
        Integer limitedQuantity
) {
    public SaleEventItem toSaleEventItem(SaleEvent saleEvent, int listPrice) {
        return SaleEventItem.builder()
                .saleEvent(saleEvent)
                .productId(productId)
                .listPrice(listPrice)
                .discountRate(discountRate)
                .limitedQuantity(limitedQuantity)
                .build();
    }
}
