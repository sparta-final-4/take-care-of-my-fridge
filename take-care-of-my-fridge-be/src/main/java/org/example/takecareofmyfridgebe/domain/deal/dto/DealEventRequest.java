package org.example.takecareofmyfridgebe.domain.deal.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

public record DealEventRequest(
        @NotNull Long candidateId,
        @NotNull SaleEventType type,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @Min(1) Integer limitedQuantity
) {
    @AssertTrue(message = "종료 시각은 시작 시각보다 늦어야 합니다.")
    public boolean isEndsAfterStarts() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    @AssertTrue(message = "번개 드롭은 한정 수량이 필요합니다.")
    public boolean isLimitedQuantityValid() {
        return type != SaleEventType.FLASH_DROP || limitedQuantity != null && limitedQuantity > 0;
    }
}
