package org.example.takecareofmyfridgebe.domain.sale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 행사 수정에서 행사 항목 하나의 새 값. 특가는 한정 수량을 보내지 않고, 번개 드롭은 반드시 보낸다.
// 행사 종류와 맞는지는 서비스가 확인한다.
public record SaleEventItemUpdateRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotNull(message = "할인율은 필수입니다.")
        @Min(value = 1, message = "할인율은 1% 이상이어야 합니다.")
        @Max(value = 99, message = "할인율은 99% 이하여야 합니다.")
        Integer discountRate,

        @Min(value = 1, message = "한정 수량은 1개 이상이어야 합니다.")
        Integer limitedQuantity
) {
}
