package org.example.takecareofmyfridgebe.domain.sale.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 번개 드롭은 상품 하나만 넣는다. 1인 구매 한도는 1개로 고정이라 받지 않는다.
public record FlashDropCreateRequest(
        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime startsAt,

        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime endsAt,

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
    @AssertTrue(message = "종료 시각은 시작 시각보다 늦어야 합니다.")
    public boolean isEndsAfterStarts() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    public SaleEvent toSaleEvent() {
        return SaleEvent.builder()
                .type(SaleEventType.FLASH_DROP)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }

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
