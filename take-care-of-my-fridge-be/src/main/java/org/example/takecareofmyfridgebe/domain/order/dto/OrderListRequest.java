package org.example.takecareofmyfridgebe.domain.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrderListRequest(
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public OrderListRequest {
        page = page == null ? 1 : page;
        size = size == null ? 10 : size;
    }
}
