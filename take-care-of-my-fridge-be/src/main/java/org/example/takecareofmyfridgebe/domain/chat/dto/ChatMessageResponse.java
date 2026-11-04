package org.example.takecareofmyfridgebe.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담 메시지 하나.
 *
 * @param recommendations AI 메시지에 붙은 레시피 추천 카드 (보여 준 순서). 이름·이미지는 지금 레시피, 임박·장볼 재료는 지금 냉장고 기준.
 *                        사용자 메시지·추천 없는 답은 빈 목록
 */
public record ChatMessageResponse(
        Long id,
        int sequence,
        ChatMessageRole role,
        String content,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,
        List<RecommendedRecipe> recommendations
) {
    public ChatMessageResponse {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    public ChatMessageResponse(Long id, int sequence, ChatMessageRole role, String content, LocalDateTime createdAt) {
        this(id, sequence, role, content, createdAt, List.of());
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSequence(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    public ChatMessageResponse withRecommendations(List<RecommendedRecipe> cards) {
        return new ChatMessageResponse(id, sequence, role, content, createdAt, cards);
    }
}
