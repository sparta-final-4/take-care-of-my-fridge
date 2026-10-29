package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionService chatSessionService;
    private final ChatProperties chatProperties;

    /**
     * 메시지 전송 전 세션을 검증한다.
     * 없거나 다른 회원의 세션이면 404(CHAT_ROOM_NOT_FOUND)를 던진다.
     * 이미 완료(COMPLETED)되었거나 만료(EXPIRED)된 끝난 세션이면 409(CHAT_ROOM_ALREADY_COMPLETED)를 던진다.
     * 마지막 메시지로부터 30분(설정값)이 초과되었으면 세션을 만료 처리(독립 트랜잭션으로 DB 반영)한 뒤 409(CHAT_SESSION_EXPIRED)를 던진다.
     *
     * @param memberId  회원 ID
     * @param sessionId 세션 ID
     */
    public void validateSessionForSending(Long memberId, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
        }

        if (session.isExpired(chatProperties.sessionTimeout(), LocalDateTime.now())) {
            chatSessionService.expireSession(sessionId);
            throw new BusinessException(ErrorCode.CHAT_SESSION_EXPIRED);
        }
    }

    /**
     * 사용자 메시지와 AI 답변 메시지 쌍을 단일 트랜잭션 내에서 순차적으로 저장하고 세션의 lastMessageAt을 갱신한다.
     * sequence는 uk_chat_messages_chat_session_id_sequence 인덱스를 활용해 현재 세션의 마지막 sequence + 1부터 채번된다.
     *
     * @param sessionId        세션 ID
     * @param userContent      사용자 메시지 내용
     * @param assistantContent 답변 메시지 내용
     * @return 저장된 두 메시지 DTO [사용자 메시지, 답변 메시지]
     */
    @Transactional
    public List<ChatMessageResponse> saveExchange(Long sessionId, String userContent, String assistantContent) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
        }

        LocalDateTime now = LocalDateTime.now();
        session.touch(now);

        int lastSequence = chatMessageRepository.findMaxSequenceBySessionId(sessionId).orElse(0);
        int userSequence = lastSequence + 1;
        int assistantSequence = lastSequence + 2;

        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .sequence(userSequence)
                .role(ChatMessageRole.USER)
                .content(userContent)
                .build();

        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .sequence(assistantSequence)
                .role(ChatMessageRole.ASSISTANT)
                .content(assistantContent)
                .build();

        ChatMessage savedUser = chatMessageRepository.save(userMessage);
        ChatMessage savedAssistant = chatMessageRepository.save(assistantMessage);

        return List.of(ChatMessageResponse.from(savedUser), ChatMessageResponse.from(savedAssistant));
    }

    /**
     * 세션의 모든 메시지 기록을 sequence 오름차순으로 조회한다.
     * 없거나 다른 회원의 세션이면 404(CHAT_ROOM_NOT_FOUND)를 던지며, 완료/만료된 세션도 조회가 가능하다.
     *
     * @param memberId  회원 ID
     * @param sessionId 세션 ID
     * @return sequence 순으로 정렬된 메시지 응답 DTO 목록
     */
    public List<ChatMessageResponse> getMessages(Long memberId, Long sessionId) {
        chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        return chatMessageRepository.findBySessionIdOrderBySequenceAsc(sessionId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
