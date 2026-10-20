package org.example.takecareofmyfridgebe.domain.cooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record CookingCompleteRequest(
        @NotNull(message = "레시피 ID는 필수입니다.")
        Long recipeId,

        @NotNull(message = "요리 결과는 필수입니다.")
        CookingResult result,

        @Size(max = 50, message = "메모는 50자 이하여야 합니다.")
        String memo,

        Long chatSessionId,

        @Size(max = 30, message = "사용한 재료는 최대 30개까지만 입력할 수 있습니다.")
        List<@Valid @NotNull(message = "재료 항목은 비어 있을 수 없습니다.") UsedIngredientRequest> usedIngredients
) {
    public CookingCompleteRequest {
        if (usedIngredients == null) {
            usedIngredients = List.of();
        }
    }

    @AssertTrue(message = "같은 재료를 두 번 보낼 수 없습니다.")
    public boolean isIngredientIdsUnique() {
        if (usedIngredients == null || usedIngredients.isEmpty()) {
            return true;
        }
        Set<Long> seenIds = new HashSet<>();
        for (UsedIngredientRequest item : usedIngredients) {
            if (item != null && item.ingredientId() != null && !seenIds.add(item.ingredientId())) {
                return false;
            }
        }
        return true;
    }

    public CookingLog toEntity(Long memberId) {
        return CookingLog.builder()
                .memberId(memberId)
                .recipeId(this.recipeId)
                .chatSessionId(this.chatSessionId)
                .result(this.result)
                .memo(this.memo)
                .build();
    }
}
