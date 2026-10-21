package org.example.takecareofmyfridgebe.domain.cooking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;

public record CookingLogUpdateRequest(
        @NotNull(message = "요리 결과는 필수입니다.")
        CookingResult result,

        @Size(max = 50, message = "메모는 50자 이하여야 합니다.")
        String memo
) {
}
