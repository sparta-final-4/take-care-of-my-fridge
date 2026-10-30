package org.example.takecareofmyfridgebe.domain.fridge.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 냉장고 재료 부분 수정 요청. 바뀐 칸만 보내고, null인 칸은 바꾸지 않는다.
 * 수정 창을 열어 둔 사이 요리 차감 등으로 바뀐 수량을 화면의 옛 값으로 덮어쓰지 않기 위해서다.
 */
public record FridgeItemUpdateRequest(
        // null은 통과, 보냈으면 공백만으로 이뤄질 수 없다
        @Pattern(regexp = "(?s).*\\S.*", message = "재료 이름을 입력해 주세요.")
        @Size(max = 200, message = "재료 이름은 200자 이하여야 합니다.")
        String rawName,

        @Positive(message = "수량은 0보다 커야 합니다.")
        BigDecimal quantity,

        StorageType storage,

        LocalDate expirationDate
) {
}
