package org.example.takecareofmyfridgebe.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;

// 사용자가 장바구니에서 본 단가와 할인 구분. 주문 시점 값과 다르면 주문하기를 거절한다.
public record OrderCreateItemRequest(
        @NotNull(message = "장바구니 상품 번호가 비어 있습니다.")
        Long cartItemId,

        @NotNull(message = "장바구니에서 본 단가가 필요합니다.")
        Integer unitPrice,

        // 할인이 없었으면 null
        SaleEventType saleType
) {
}
