package org.example.takecareofmyfridgebe.domain.fridge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FridgeItemCreateRequest(
        @NotEmpty(message = "등록할 재료를 1개 이상 입력해 주세요.")
        @Size(max = 20, message = "한 번에 최대 20개까지만 등록할 수 있습니다.")
        List<@Valid @NotNull(message = "재료 항목은 비어 있을 수 없습니다.") Item> items
) {
    public record Item(
            @NotBlank(message = "재료 이름을 입력해 주세요.")
            @Size(max = 200, message = "재료 이름은 200자 이하여야 합니다.")
            String rawName,

            @NotNull(message = "수량을 입력해 주세요.")
            @Positive(message = "수량은 0보다 커야 합니다.")
            BigDecimal quantity,

            StorageType storage,

            LocalDate expirationDate
    ) {
        public Fridge toEntity(
                Long memberId,
                Ingredient ingredient,
                StorageType resolvedStorage,
                LocalDate resolvedExpirationDate
        ) {
            return Fridge.builder()
                    .memberId(memberId)
                    .ingredient(ingredient)
                    .rawName(rawName)
                    .quantity(quantity)
                    .storage(resolvedStorage)
                    .expirationDate(resolvedExpirationDate)
                    .source(FridgeSource.MANUAL)
                    .build();
        }
    }
}
