package org.example.takecareofmyfridgebe.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequest(
        @NotEmpty(message = "주문할 상품을 선택해 주세요.")
        @Size(max = 50, message = "한 번에 최대 50개까지 주문할 수 있습니다.")
        List<@Valid @NotNull(message = "주문할 상품 항목이 비어 있습니다.") OrderCreateItemRequest> items
) {
    public List<Long> cartItemIds() {
        return items.stream().map(OrderCreateItemRequest::cartItemId).distinct().toList();
    }
}
