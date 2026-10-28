package org.example.takecareofmyfridgebe.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;

import java.time.LocalDateTime;

public record ChatSessionResponse(
        Long id,
        ChatSessionStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime lastMessageAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt
) {
    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getLastMessageAt(),
                session.getCreatedAt()
        );
    }
}
