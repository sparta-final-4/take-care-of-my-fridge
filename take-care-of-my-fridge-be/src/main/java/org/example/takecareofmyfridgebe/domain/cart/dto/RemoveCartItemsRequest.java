package org.example.takecareofmyfridgebe.domain.cart.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RemoveCartItemsRequest(
        @NotEmpty(message = "cartItemIds는 1개 이상이어야 합니다.")
        List<Long> cartItemIds
) {
}
