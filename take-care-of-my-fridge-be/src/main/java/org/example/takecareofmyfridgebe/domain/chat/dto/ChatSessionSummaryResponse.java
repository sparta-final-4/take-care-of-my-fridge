package org.example.takecareofmyfridgebe.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;

import java.time.LocalDateTime;

public record ChatSessionSummaryResponse(
        Long sessionId,
        ChatSessionStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime lastMessageAt,
        long messageCount,
        String firstQuestion,
        String lastMessagePreview
) {
    static final int PREVIEW_MAX_LENGTH = 50;

    /**
     * @param status 화면에 보일 상태. 시간 초과로 아직 ACTIVE인 상담은 서비스가 EXPIRED로 넘긴다.
     */
    public static ChatSessionSummaryResponse of(ChatSession session, ChatSessionStatus status, ChatSessionMessageStats stats) {
        return new ChatSessionSummaryResponse(
                session.getId(),
                status,
                session.getCreatedAt(),
                session.getLastMessageAt(),
                stats == null ? 0 : stats.messageCount(),
                truncate(stats == null ? null : stats.firstQuestion()),
                truncate(stats == null ? null : stats.lastMessage())
        );
    }

    // 이모지 등 서로게이트 쌍이 반으로 잘리지 않도록 코드 포인트 기준으로 자른다
    private static String truncate(String content) {
        if (content == null || content.codePointCount(0, content.length()) <= PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, PREVIEW_MAX_LENGTH));
    }
}
