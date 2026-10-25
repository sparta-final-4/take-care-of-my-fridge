package org.example.takecareofmyfridgebe.domain.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

// 시작 전인 행사를 고친다. 행사에 든 상품은 빠짐없이 한 번씩 보내고, 상품을 넣거나 빼지는 않는다.
public record SaleEventUpdateRequest(
        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime startsAt,

        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime endsAt,

        @NotEmpty(message = "상품을 하나 이상 보내 주세요.")
        List<@Valid @NotNull(message = "상품 항목은 비어 있을 수 없습니다.") SaleEventItemUpdateRequest> items
) {
    @AssertTrue(message = "종료 시각은 시작 시각보다 늦어야 합니다.")
    public boolean isEndsAfterStarts() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    @AssertTrue(message = "같은 상품을 두 번 보낼 수 없습니다.")
    public boolean isProductIdsUnique() {
        if (items == null) {
            return true;
        }
        List<Long> productIds = items.stream()
                .filter(Objects::nonNull)
                .map(SaleEventItemUpdateRequest::productId)
                .toList();
        return productIds.stream().distinct().count() == productIds.size();
    }
}
