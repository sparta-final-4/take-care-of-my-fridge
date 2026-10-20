package org.example.takecareofmyfridgebe.domain.fridge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record FridgeItemLogSearchRequest(
        // 비우면 먹었어요·버렸어요 전체
        FridgeItemAction action,
        // 비우면 기간 제한 없음. created_at 기준이며 to 날짜 하루 전체를 포함한다.
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public FridgeItemLogSearchRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
