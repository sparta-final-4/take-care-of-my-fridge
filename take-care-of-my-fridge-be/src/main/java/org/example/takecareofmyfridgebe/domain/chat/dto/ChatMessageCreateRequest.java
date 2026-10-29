package org.example.takecareofmyfridgebe.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageCreateRequest(
        @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
        @Size(max = 500, message = "메시지는 최대 500자까지 입력할 수 있습니다.")
        String content
) {
}
