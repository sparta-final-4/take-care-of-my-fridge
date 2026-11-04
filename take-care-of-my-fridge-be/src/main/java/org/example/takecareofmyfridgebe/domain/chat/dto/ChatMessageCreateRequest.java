package org.example.takecareofmyfridgebe.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * 상담 메시지 전송 요청.
 *
 * @param content               사용자 메시지 (칩이면 칩 문장)
 * @param mode                  처리 방식. 없으면 AUTO
 * @param selectedIngredientIds 내 냉장고에서 고른 재료 ID. 없으면 빈 목록 (null·중복은 뺀다)
 * @param useSelectedOnly       고른 재료를 꼭 쓰는 레시피만 추천할지. 없으면 false
 */
public record ChatMessageCreateRequest(
        @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
        @Size(max = 500, message = "메시지는 최대 500자까지 입력할 수 있습니다.")
        String content,
        ChatMessageMode mode,
        @Size(max = 50, message = "선택 재료는 최대 50개까지 보낼 수 있습니다.")
        List<Long> selectedIngredientIds,
        Boolean useSelectedOnly
) {
    public ChatMessageCreateRequest {
        mode = mode == null ? ChatMessageMode.AUTO : mode;
        selectedIngredientIds = selectedIngredientIds == null
                ? List.of()
                : selectedIngredientIds.stream().filter(Objects::nonNull).distinct().toList();
        useSelectedOnly = Boolean.TRUE.equals(useSelectedOnly);
    }

    // 내용만 있는 일반 메시지 (mode=AUTO, 선택 재료 없음)
    public static ChatMessageCreateRequest of(String content) {
        return new ChatMessageCreateRequest(content, null, null, null);
    }
}
