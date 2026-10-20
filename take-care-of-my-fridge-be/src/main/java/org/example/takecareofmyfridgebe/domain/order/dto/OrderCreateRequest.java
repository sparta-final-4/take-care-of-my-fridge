package org.example.takecareofmyfridgebe.domain.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequest(
        @NotEmpty(message = "주문할 상품을 선택해 주세요.")
        @Size(max = 50, message = "한 번에 최대 50개까지 주문할 수 있습니다.")
        List<@NotNull(message = "장바구니 상품 번호가 비어 있습니다.") Long> cartItemIds
) {
}
