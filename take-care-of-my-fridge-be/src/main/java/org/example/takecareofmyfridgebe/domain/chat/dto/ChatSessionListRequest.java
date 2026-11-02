package org.example.takecareofmyfridgebe.domain.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChatSessionListRequest(
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size,
        ChatSessionListStatus status
) {
    public ChatSessionListRequest {
        page = page == null ? 1 : page;
        size = size == null ? 10 : size;
        status = status == null ? ChatSessionListStatus.ALL : status;
    }
}
