package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionMessageStats;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.event.ChatSessionChangedEvent;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatRecommendationRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRecommendationRepository chatRecommendationRepository;
    private final ChatProperties chatProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 회원의 진행 중(ACTIVE) 상담 세션을 조회하거나, 없으면 신규 생성하여 반환한다.
     * 진행 중 세션이 30분(설정값)을 초과한 경우 만료(EXPIRED) 처리 후 새 세션을 생성하여 반환한다.
     * (member_id, status) 복합 인덱스를 활용해 조회한다.
     * 저장 중 uk_chat_sessions_active_member_id 유니크 제약 위반이 발생하면,
     * 동시 요청에 의해 먼저 세션이 생성된 경우이므로 다시 진행 중 세션을 조회하여 반환한다.
     * 새로 만들었을 때만 상담 변경(ACTIVE) 이벤트를 발행한다. 트랜잭션이 없으므로 리스너가 바로 받는다.
     *
     * @param memberId 회원 ID
     * @return 진행 중 상담 세션 응답 DTO
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatSessionResponse getOrCreateActiveSession(Long memberId) {
        return chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE)
                .map(session -> {
                    if (session.isExpired(chatProperties.sessionTimeout(), LocalDateTime.now())) {
                        expireSession(session.getId());
                        return saveNewActiveSession(memberId);
                    }
                    return ChatSessionResponse.from(session);
                })
                .orElseGet(() -> saveNewActiveSession(memberId));
    }

    /**
     * 특정 상담 세션을 만료(EXPIRED) 처리하고 즉시 커밋/플러시한다.
     * ACTIVE 상태일 때만 EXPIRED로 전이하며, 이미 완료/만료된 경우는 아무 동작도 하지 않는다.
     * 호출 측의 트랜잭션(예: readOnly 트랜잭션)이나 예외 발생 여부와 무관하게 즉시 커밋되도록 REQUIRES_NEW로 실행한다.
     * 실제로 만료시켰을 때만 상담 변경(EXPIRED) 이벤트를 발행한다.
     * getOrCreateActiveSession 안에서 부를 때는 자기 호출이라 트랜잭션 없이 실행되고, 이벤트는 저장 직후 바로 나간다.
     *
     * @param sessionId 세션 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireSession(Long sessionId) {
        chatSessionRepository.findById(sessionId)
                .ifPresent(session -> {
                    if (session.isActive()) {
                        session.expire();
                        chatSessionRepository.saveAndFlush(session);
                        eventPublisher.publishEvent(ChatSessionChangedEvent.from(session));
                    }
                });
    }

    private ChatSessionResponse saveNewActiveSession(Long memberId) {
        try {
            ChatSession session = chatSessionRepository.saveAndFlush(
                    ChatSession.builder()
                            .memberId(memberId)
                            .build()
            );
            eventPublisher.publishEvent(ChatSessionChangedEvent.from(session));
            return ChatSessionResponse.from(session);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase().contains("uk_chat_sessions_active_member_id")) {
                return chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE)
                        .map(ChatSessionResponse::from)
                        .orElseThrow(() -> e);
            }
            throw e;
        }
    }

    /**
     * 회원의 특정 상담 세션을 조회한다.
     * 존재하지 않거나 다른 회원의 세션이면 404(CHAT_ROOM_NOT_FOUND)를 던진다.
     *
     * @param memberId  회원 ID
     * @param sessionId 세션 ID
     * @return 상담 세션 응답 DTO
     */
    public ChatSessionResponse getSession(Long memberId, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        return ChatSessionResponse.from(session);
    }

    /**
     * 회원의 상담 내역을 상태 필터에 맞춰 최근 시작순으로 페이지 조회하고, 탭별 건수를 함께 준다. 메시지가 없는 세션은 제외한다.
     * 세션 목록 1회 + 세션 ID 목록 기준 메시지 수·첫 질문·마지막 메시지 1회 + 탭 건수 1회로 조회해 N+1을 피한다
     * (전체 개수가 필요하면 count 1회 추가).
     * 시간 초과로 아직 ACTIVE인 상담은 다음 상담 시작 때 만료되므로 목록·필터·건수 모두 만료(EXPIRED)로 본다.
     *
     * @param memberId 회원 ID
     * @param request  페이지(1부터)·크기·상태(ALL/ACTIVE/ENDED)
     * @return 탭 건수와 상담 요약 페이지
     */
    public ChatSessionListResponse getMySessions(Long memberId, ChatSessionListRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activeSince = now.minus(chatProperties.sessionTimeout());

        Page<ChatSession> sessions = chatSessionRepository.findMySessionsWithMessages(
                memberId,
                request.status(),
                activeSince,
                PageRequest.of(request.page() - 1, request.size())
        );

        Map<Long, ChatSessionMessageStats> statsBySessionId = chatMessageRepository
                .findStatsBySessionIds(sessions.map(ChatSession::getId).getContent())
                .stream()
                .collect(Collectors.toMap(ChatSessionMessageStats::sessionId, Function.identity()));

        PageResponse<ChatSessionSummaryResponse> page = PageResponse.of(sessions.map(session ->
                ChatSessionSummaryResponse.of(
                        session,
                        session.isExpired(chatProperties.sessionTimeout(), now) ? ChatSessionStatus.EXPIRED : session.getStatus(),
                        statsBySessionId.get(session.getId())
                )));

        return new ChatSessionListResponse(
                chatSessionRepository.countMySessionsWithMessages(memberId, activeSince),
                page
        );
    }

    /**
     * 회원이 상담 세션을 명시적으로 종료(COMPLETED) 처리한다.
     * 존재하지 않거나 다른 회원의 세션이면 404(CHAT_ROOM_NOT_FOUND)를 던진다.
     * 이미 완료(COMPLETED)되었거나 만료(EXPIRED)된 세션이면 409(CHAT_ROOM_ALREADY_COMPLETED)를 던진다.
     * 커밋 뒤 상담 변경(COMPLETED) 신호가 나가도록 이벤트를 발행한다.
     *
     * @param memberId  회원 ID
     * @param sessionId 세션 ID
     * @return 종료된 상담 세션 응답 DTO
     */
    @Transactional
    public ChatSessionResponse completeSession(Long memberId, Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (session.getStatus() == ChatSessionStatus.COMPLETED || session.getStatus() == ChatSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
        }

        session.complete();
        eventPublisher.publishEvent(ChatSessionChangedEvent.from(session));
        return ChatSessionResponse.from(session);
    }

    /**
     * 요리 완료를 이을 상담 ID를 찾는다. 찾지 못하면 비어 있고, 예외를 던지지 않는다.
     * <ul>
     *   <li>요청에 상담 ID가 있으면: 내 상담이고 진행 중(ACTIVE)이며 시간이 지나지 않았으면 그 상담 (추천 여부는 보지 않는다)</li>
     *   <li>없으면: 진행 중 상담이 시간이 지나지 않았고, 그 상담에서 이 레시피를 레시피 카드로 추천한 적 있으면 그 상담</li>
     * </ul>
     * 남의 상담·끝난 상담·시간이 지난 상담은 잇지 않을 뿐 요리 완료를 막지 않는다.
     * 요리 완료 트랜잭션 밖(NOT_SUPPORTED)에서 읽는다. 안에서 읽다 예외가 나면 요리 완료 트랜잭션이 롤백 전용으로 표시되기 때문이다.
     * 조회는 상담 1번 + (서버가 찾을 때) 추천 1번.
     *
     * @param memberId           회원 ID
     * @param requestedSessionId 요청에 담긴 상담 ID (없으면 null)
     * @param recipeId           요리 완료한 레시피 ID
     * @return 이을 상담 ID
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<Long> findCookingSessionId(Long memberId, Long requestedSessionId, Long recipeId) {
        LocalDateTime now = LocalDateTime.now();
        if (requestedSessionId != null) {
            return chatSessionRepository.findByIdAndMemberId(requestedSessionId, memberId)
                    .filter(session -> isOpen(session, now))
                    .map(ChatSession::getId);
        }
        return chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE)
                .filter(session -> isOpen(session, now))
                .map(ChatSession::getId)
                .filter(sessionId -> chatRecommendationRepository.existsRecipeInSession(sessionId, recipeId));
    }

    // 진행 중이고 시간이 지나지 않은 상담 (시간이 지난 ACTIVE 상담은 목록에서 이미 만료로 보인다)
    private boolean isOpen(ChatSession session, LocalDateTime now) {
        return session.isActive() && !session.isExpired(chatProperties.sessionTimeout(), now);
    }

    /**
     * 요리 완료로 상담을 종료(COMPLETED)한다. 요리 결과(성공·실패)와 상관없이 끝낸다.
     * 내 상담이고 진행 중(ACTIVE)일 때만 끝내며, 없거나 이미 끝난 상담은 건너뛴다(예외를 던지지 않는다).
     * 종료했을 때만 상담 변경(COMPLETED) 이벤트를 발행하며, 이 트랜잭션이 커밋된 뒤 신호가 나간다.
     * 요리 완료 커밋 뒤(AFTER_COMMIT) 리스너가 부른다. 그때는 끝난 요리 트랜잭션에 참여하면 저장되지 않으므로 새 트랜잭션으로 실행한다.
     *
     * @param memberId  요청 회원 ID
     * @param sessionId 채팅 세션 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSessionFromCooking(Long memberId, Long sessionId) {
        chatSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .filter(ChatSession::isActive)
                .ifPresent(session -> {
                    session.complete();
                    eventPublisher.publishEvent(ChatSessionChangedEvent.from(session));
                });
    }
}
