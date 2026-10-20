package org.example.takecareofmyfridgebe.domain.cooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record CookingLogSearchRequest(
        // 비우면 전체 레시피
        Long recipeId,
        // 비우면 기간 제한 없음. created_at 기준이며 to 날짜 하루 전체를 포함한다.
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public CookingLogSearchRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
