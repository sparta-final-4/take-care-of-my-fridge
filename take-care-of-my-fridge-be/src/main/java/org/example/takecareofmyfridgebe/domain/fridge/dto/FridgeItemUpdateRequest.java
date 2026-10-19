package org.example.takecareofmyfridgebe.domain.fridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FridgeItemUpdateRequest(
        @NotBlank(message = "재료 이름을 입력해 주세요.")
        @Size(max = 200, message = "재료 이름은 200자 이하여야 합니다.")
        String rawName,

        @NotNull(message = "수량을 입력해 주세요.")
        @Positive(message = "수량은 0보다 커야 합니다.")
        BigDecimal quantity,

        @NotNull(message = "보관방식을 선택해 주세요.")
        StorageType storage,

        @NotNull(message = "소비기한을 입력해 주세요.")
        LocalDate expirationDate
) {
}
