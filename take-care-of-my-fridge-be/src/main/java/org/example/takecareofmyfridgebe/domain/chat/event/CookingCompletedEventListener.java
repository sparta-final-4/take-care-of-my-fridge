package org.example.takecareofmyfridgebe.domain.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.cooking.event.CookingCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 요리 완료가 커밋된 뒤 이은 상담을 끝낸다. 요리 완료는 이미 끝났으므로 상담 종료가 실패해도 되돌리지 않고 로그만 남긴다.
// 종료는 새 트랜잭션(REQUIRES_NEW)에서 하고, 그 트랜잭션이 커밋되면 기존 상담 변경(COMPLETED) 신호가 나간다
@Slf4j
@Component
@RequiredArgsConstructor
public class CookingCompletedEventListener {

    private final ChatSessionService chatSessionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCookingCompleted(CookingCompletedEvent event) {
        try {
            chatSessionService.completeSessionFromCooking(event.memberId(), event.chatSessionId());
        } catch (RuntimeException e) {
            log.error("요리 완료 상담 종료 실패: memberId={}, chatSessionId={}, cookingLogId={}",
                    event.memberId(), event.chatSessionId(), event.cookingLogId(), e);
        }
    }
}
