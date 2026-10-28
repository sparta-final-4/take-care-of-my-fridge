package org.example.takecareofmyfridgebe.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        int sequence,
        ChatMessageRole role,
        String content,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSequence(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
