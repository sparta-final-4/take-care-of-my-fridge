package org.example.takecareofmyfridgebe.domain.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 번개 드롭은 고른 상품마다 행사가 하나씩 만들어지고, 상품마다 한정 수량이 따로 있다.
public record FlashDropCreateRequest(
        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime startsAt,

        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime endsAt,

        @NotEmpty(message = "번개 드롭 상품을 하나 이상 넣어 주세요.")
        List<@Valid @NotNull(message = "번개 드롭 상품 항목은 비어 있을 수 없습니다.") FlashDropItemRequest> items
) {
    @AssertTrue(message = "종료 시각은 시작 시각보다 늦어야 합니다.")
    public boolean isEndsAfterStarts() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    @AssertTrue(message = "같은 상품을 두 번 넣을 수 없습니다.")
    public boolean isProductIdsUnique() {
        if (items == null) {
            return true;
        }
        List<Long> productIds = productIds();
        return productIds.stream().distinct().count() == productIds.size();
    }

    public List<Long> productIds() {
        return items.stream()
                .filter(Objects::nonNull)
                .map(FlashDropItemRequest::productId)
                .toList();
    }

    public SaleEvent toSaleEvent() {
        return SaleEvent.builder()
                .type(SaleEventType.FLASH_DROP)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }
}
