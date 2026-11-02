package org.example.takecareofmyfridgebe.domain.chat.event;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.push.ChatSessionPushPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 상담 변경이 커밋된 뒤 모든 서버에 신호를 보낸다. 롤백되면 보내지 않는다.
// 상담 시작(getOrCreateActiveSession)은 트랜잭션 없이 저장을 끝내므로 fallbackExecution으로 바로 보낸다.
// 발행 실패는 발행기가 로그만 남기므로 상담 변경을 되돌리지 않는다
@Component
@RequiredArgsConstructor
public class ChatSessionChangedEventListener {

    private final ChatSessionPushPublisher chatSessionPushPublisher;

    // 상담 시작(getOrCreateActiveSession)은 트랜잭션이 없어서, 이 값이 없으면 시작 신호가 오류 없이 사라진다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatSessionChanged(ChatSessionChangedEvent event) {
        chatSessionPushPublisher.publish(event.memberId(), event.sessionId(), event.status());
    }
}
