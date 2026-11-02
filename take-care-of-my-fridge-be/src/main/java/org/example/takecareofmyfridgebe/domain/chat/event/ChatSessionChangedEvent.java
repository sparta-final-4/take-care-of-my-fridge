package org.example.takecareofmyfridgebe.domain.chat.event;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;

// 상담이 바뀌었다는 사실만 싣는다(새 상담 시작, 답 저장, 종료, 만료). 메시지 내용은 싣지 않는다.
public record ChatSessionChangedEvent(
        Long memberId,
        Long sessionId,
        ChatSessionStatus status
) {

    public static ChatSessionChangedEvent from(ChatSession session) {
        return new ChatSessionChangedEvent(session.getMemberId(), session.getId(), session.getStatus());
    }
}
