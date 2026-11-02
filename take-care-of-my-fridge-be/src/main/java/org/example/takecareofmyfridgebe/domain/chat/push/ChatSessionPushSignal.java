package org.example.takecareofmyfridgebe.domain.chat.push;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;

// SSE event: chat의 data. 받는 사람은 연결로 정해지므로 회원 번호는 뺀다
public record ChatSessionPushSignal(
        Long sessionId,
        ChatSessionStatus status
) {

    public static ChatSessionPushSignal from(ChatSessionPushMessage message) {
        return new ChatSessionPushSignal(message.sessionId(), message.status());
    }
}
