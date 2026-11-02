package org.example.takecareofmyfridgebe.domain.chat.dto;

// 상담 목록 조립용: 세션별 메시지 수, 첫 사용자 메시지 원문, 마지막 메시지 원문
public record ChatSessionMessageStats(
        Long sessionId,
        long messageCount,
        String firstQuestion,
        String lastMessage
) {
}
