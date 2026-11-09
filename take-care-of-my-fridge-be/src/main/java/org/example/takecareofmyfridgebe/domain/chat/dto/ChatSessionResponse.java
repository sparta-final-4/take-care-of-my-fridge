package org.example.takecareofmyfridgebe.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;

import java.time.LocalDateTime;

/**
 * 상담 세션.
 *
 * @param cookedRecipe 요리 완료로 끝난 상담이면 그 요리 (상담 단건 조회만 채운다. 직접 끝냈거나 진행 중·만료면 null)
 */
public record ChatSessionResponse(
        Long id,
        ChatSessionStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime lastMessageAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,
        CookedRecipe cookedRecipe
) {
    public record CookedRecipe(Long recipeId, String name) {
    }

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getLastMessageAt(),
                session.getCreatedAt(),
                null
        );
    }

    public ChatSessionResponse withCookedRecipe(CookedRecipe recipe) {
        return new ChatSessionResponse(id, status, lastMessageAt, createdAt, recipe);
    }
}
