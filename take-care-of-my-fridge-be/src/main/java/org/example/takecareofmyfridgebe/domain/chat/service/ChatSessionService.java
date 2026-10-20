package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    /**
     * 회원의 채팅 세션을 검증하고 조회한다.
     * 존재하지 않거나 다른 회원의 세션이면 404(CHAT_ROOM_NOT_FOUND) 예외를 던진다.
     *
     * @param memberId  요청 회원 ID
     * @param sessionId 채팅 세션 ID
     * @return 검증된 채팅 세션 엔티티
     */
    public ChatSession validateAndGetSession(Long memberId, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (session.isCompleted()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
        }

        return session;
    }

    /**
     * 요리 완료 후 연관된 채팅 세션을 종료(COMPLETED) 처리한다.
     * ACTIVE 상태일 때만 complete()를 호출하며, 이미 종료(COMPLETED)되거나 만료(EXPIRED)된 세션은 건너뛴다.
     *
     * @param memberId  요청 회원 ID
     * @param sessionId 채팅 세션 ID
     */
    @Transactional
    public void completeSessionFromCooking(Long memberId, Long sessionId) {
        ChatSession session = validateAndGetSession(memberId, sessionId);
        if (session.isActive()) {
            session.complete();
        }
    }
}
