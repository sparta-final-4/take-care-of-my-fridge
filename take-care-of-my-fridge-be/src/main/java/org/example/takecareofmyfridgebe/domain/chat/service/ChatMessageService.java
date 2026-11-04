package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationToSave;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.event.ChatSessionChangedEvent;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatRecommendationRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRecommendationRepository chatRecommendationRepository;
    private final ChatSessionService chatSessionService;
    private final ChatProperties chatProperties;
    private final ApplicationEventPublisher eventPublisher;

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
     * 저장 뒤 상담 변경(ACTIVE) 이벤트를 발행해, 커밋되면 다른 창이 메시지 기록을 다시 받게 한다.
     *
     * @param sessionId        세션 ID
     * @param userContent      사용자 메시지 내용
     * @param assistantContent 답변 메시지 내용
     * @return 저장된 두 메시지 DTO [사용자 메시지, 답변 메시지]
     */
    @Transactional
    public List<ChatMessageResponse> saveExchange(Long sessionId, String userContent, String assistantContent) {
        return saveExchange(sessionId, userContent, assistantContent, List.of());
    }

    /**
     * 사용자 메시지와 AI 답을 저장하고, AI 답에 붙은 레시피 추천 카드를 같은 트랜잭션에서 순서대로 저장한다 (카드가 없으면 저장하지 않음).
     *
     * @param recommendations 보여 준 순서대로의 레시피 추천 (display_order 0부터)
     */
    @Transactional
    public List<ChatMessageResponse> saveExchange(Long sessionId, String userContent, String assistantContent,
                                                  List<RecommendationToSave> recommendations) {
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
        for (int order = 0; order < recommendations.size(); order++) {
            RecommendationToSave recommendation = recommendations.get(order);
            chatRecommendationRepository.save(ChatRecommendation.builder()
                    .message(savedAssistant)
                    .recipeId(recommendation.recipeId())
                    .displayOrder(order)
                    .description(recommendation.description())
                    .build());
        }
        eventPublisher.publishEvent(ChatSessionChangedEvent.from(session));

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
    /**
     * AI 메시지 여러 개에 붙은 레시피 추천을 한 번에 조회한다 (메시지 id, 보여 준 순서대로).
     */
    public List<SavedRecommendation> findRecipeRecommendations(Collection<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        return chatRecommendationRepository.findRecipeRecommendationsByMessageIds(messageIds).stream()
                .map(SavedRecommendation::from)
                .toList();
    }

    /**
     * 상담마다 최근에 추천한 레시피 ID를 limit개까지 (최근 답부터, 카드 순서, 중복 제외). 상담 수와 상관없이 쿼리 한 번이다.
     */
    public Map<Long, List<Long>> findRecentRecommendedRecipeIds(Collection<Long> sessionIds, int limit) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> bySession = new LinkedHashMap<>();
        for (ChatRecommendation recommendation : chatRecommendationRepository.findRecipeRecommendationsBySessionIds(sessionIds)) {
            List<Long> ids = bySession.computeIfAbsent(recommendation.getMessage().getSession().getId(), id -> new ArrayList<>());
            if (ids.size() < limit && !ids.contains(recommendation.getRecipeId())) {
                ids.add(recommendation.getRecipeId());
            }
        }
        return bySession;
    }

    // 이 상담에서 이미 추천한 레시피 ID (다음 추천에서 뒤로 보낸다)
    public Set<Long> findRecommendedRecipeIds(Long sessionId) {
        return Set.copyOf(chatRecommendationRepository.findRecipeIdsBySessionId(sessionId));
    }

    public List<ChatMessageResponse> getMessages(Long memberId, Long sessionId) {
        chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        return chatMessageRepository.findBySessionIdOrderBySequenceAsc(sessionId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
