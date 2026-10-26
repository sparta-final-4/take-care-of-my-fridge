package org.example.takecareofmyfridgebe.domain.refund.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;

import java.util.List;

public record RefundCreateRequest(
        @NotNull(message = "주문 ID가 비어 있습니다.")
        Long orderId,

        @NotEmpty(message = "환불할 상품을 선택해 주세요.")
        List<@NotNull(message = "환불할 상품 정보가 비어 있습니다.") @Valid Item> items,

        @NotNull(message = "환불 사유를 선택해 주세요.")
        RefundReasonType reasonType,

        @Size(max = 500, message = "상세 사유는 500자까지 입력할 수 있습니다.")
        String reasonDetail
) {

    public record Item(
            @NotNull(message = "주문 상품 번호가 비어 있습니다.")
            Long orderItemId,

            @Min(value = 1, message = "환불 수량은 1 이상이어야 합니다.")
            int quantity
    ) {
    }
}
