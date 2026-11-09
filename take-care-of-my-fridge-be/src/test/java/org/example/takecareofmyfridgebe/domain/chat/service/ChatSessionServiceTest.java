package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.event.ChatSessionChangedEvent;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatRecommendationRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatRecommendationRepository chatRecommendationRepository;

    @Mock
    private ChatProperties chatProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        lenient().when(chatProperties.sessionTimeout()).thenReturn(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("진행 중인 세션이 없으면 새로 만들어 반환한다")
    void createsNewSession_whenNoActiveSession() {
        Long memberId = 1L;
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(chatSessionRepository.saveAndFlush(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        ChatSessionResponse response = chatSessionService.getOrCreateActiveSession(memberId);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(ChatSessionStatus.ACTIVE);
        verify(chatSessionRepository, times(1)).saveAndFlush(any(ChatSession.class));
    }

    @Test
    @DisplayName("진행 중인 세션이 이미 있으면 새 세션을 만들지 않고 기존 세션을 반환한다")
    void returnsExistingSession_whenActiveSessionExists() {
        Long memberId = 1L;
        ChatSession existingSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(existingSession, "id", 50L);

        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSession));

        ChatSessionResponse response = chatSessionService.getOrCreateActiveSession(memberId);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo(ChatSessionStatus.ACTIVE);
        verify(chatSessionRepository, never()).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("진행 중인 세션이 30분 지난 경우 기존 세션을 만료시키고 새 세션을 만들어 반환한다")
    void expiresOldAndCreatesNew_whenActiveSessionIsTimedOut() {
        Long memberId = 1L;
        Long expiredSessionId = 50L;
        ChatSession expiredSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(expiredSession, "id", expiredSessionId);
        ReflectionTestUtils.setField(expiredSession, "lastMessageAt", LocalDateTime.now().minusMinutes(35));

        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.of(expiredSession));
        when(chatSessionRepository.findById(expiredSessionId))
                .thenReturn(Optional.of(expiredSession));

        when(chatSessionRepository.saveAndFlush(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 200L);
            }
            return saved;
        });

        ChatSessionResponse response = chatSessionService.getOrCreateActiveSession(memberId);

        assertThat(expiredSession.getStatus()).isEqualTo(ChatSessionStatus.EXPIRED);
        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("expireSession: ACTIVE 세션을 EXPIRED로 상태 변경하고 saveAndFlush를 호출한다")
    void marksExpiredAndFlushes_whenExpiringActiveSession() {
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(session, "id", sessionId);

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        chatSessionService.expireSession(sessionId);

        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.EXPIRED);
        verify(chatSessionRepository, times(1)).saveAndFlush(session);
    }

    @Test
    @DisplayName("본인의 세션 단건 조회 시 정상적으로 반환한다")
    void returnsOwnSession() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId))
                .thenReturn(Optional.of(session));

        ChatSessionResponse response = chatSessionService.getSession(memberId, sessionId);

        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.status()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않거나 남의 세션을 조회하면 404 CHAT_ROOM_NOT_FOUND 예외를 던진다")
    void throwsNotFound_whenGettingOthersOrMissingSession() {
        Long memberId = 1L;
        Long sessionId = 999L;

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.getSession(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않거나 남의 세션을 종료하려고 하면 404 CHAT_ROOM_NOT_FOUND 예외를 던진다")
    void throwsNotFound_whenCompletingOthersOrMissingSession() {
        Long memberId = 1L;
        Long sessionId = 999L;

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.completeSession(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("진행 중인 세션을 정상적으로 종료(COMPLETED)한다")
    void completesActiveSession() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId))
                .thenReturn(Optional.of(session));

        ChatSessionResponse response = chatSessionService.completeSession(memberId, sessionId);

        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.status()).isEqualTo(ChatSessionStatus.COMPLETED);
        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("이미 완료(COMPLETED)된 세션을 종료하려고 하면 409 CHAT_ROOM_ALREADY_COMPLETED 예외를 던진다")
    void throwsAlreadyCompleted_whenSessionIsCompleted() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.complete(); // COMPLETED 상태로 변경

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatSessionService.completeSession(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("이미 만료(EXPIRED)된 세션을 종료하려고 하면 409 CHAT_ROOM_ALREADY_COMPLETED 예외를 던진다")
    void throwsAlreadyCompleted_whenSessionIsExpired() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.expire(); // EXPIRED 상태로 변경

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatSessionService.completeSession(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("세션 종료 후 다시 시작하면 새로운 세션을 생성한다")
    void createsNewSession_whenPreviousSessionIsCompleted() {
        Long memberId = 1L;
        Long oldSessionId = 10L;
        Long newSessionId = 11L;

        // 1. 기존 세션 종료
        ChatSession oldSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(oldSession, "id", oldSessionId);
        when(chatSessionRepository.findByIdAndMemberId(oldSessionId, memberId))
                .thenReturn(Optional.of(oldSession));

        chatSessionService.completeSession(memberId, oldSessionId);
        assertThat(oldSession.getStatus()).isEqualTo(ChatSessionStatus.COMPLETED);

        // 2. 종료 후 다시 세션 시작 요청: ACTIVE 상태 세션이 없으므로 신규 생성
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(chatSessionRepository.saveAndFlush(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newSessionId);
            return saved;
        });

        ChatSessionResponse newResponse = chatSessionService.getOrCreateActiveSession(memberId);

        assertThat(newResponse.id()).isEqualTo(newSessionId);
        assertThat(newResponse.status()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("세션 저장 중 uk_chat_sessions_active_member_id 유니크 위반이 발생하면 기존 진행 중 세션을 반환한다")
    void returnsExistingSession_whenDuplicateKeyOccurs() {
        Long memberId = 1L;
        Long existingSessionId = 77L;
        ChatSession existingSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(existingSession, "id", existingSessionId);

        // 1. 처음 조회 시에는 동시성 타이밍으로 인해 없었음
        // 2. 저장 실패 후 다시 조회 시에는 다른 요청이 생성한 세션이 조회됨
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingSession));

        when(chatSessionRepository.saveAndFlush(any(ChatSession.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1' for key 'uk_chat_sessions_active_member_id'"));

        ChatSessionResponse response = chatSessionService.getOrCreateActiveSession(memberId);

        assertThat(response.id()).isEqualTo(existingSessionId);
        assertThat(response.status()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("세션 저장 중 uk_chat_sessions_active_member_id가 아닌 다른 제약 위반이 발생하면 예외를 그대로 다시 던진다")
    void rethrows_whenOtherConstraintFails() {
        Long memberId = 1L;

        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(chatSessionRepository.saveAndFlush(any(ChatSession.class)))
                .thenThrow(new DataIntegrityViolationException("Cannot add or update a child row: foreign key constraint fails"));

        assertThatThrownBy(() -> chatSessionService.getOrCreateActiveSession(memberId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key constraint fails");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 새 상담을 만들면 ACTIVE로 발행한다")
    void publishesActive_whenNewSessionIsCreated() {
        Long memberId = 1L;
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.saveAndFlush(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        chatSessionService.getOrCreateActiveSession(memberId);

        verify(eventPublisher).publishEvent(new ChatSessionChangedEvent(memberId, 100L, ChatSessionStatus.ACTIVE));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 진행 중 상담을 그대로 돌려줄 때는 발행하지 않는다")
    void doesNotPublish_whenExistingSessionIsReturned() {
        Long memberId = 1L;
        ChatSession existingSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(existingSession, "id", 50L);
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSession));

        chatSessionService.getOrCreateActiveSession(memberId);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 저장 경합으로 다른 요청이 만든 상담을 돌려줄 때는 발행하지 않는다")
    void doesNotPublish_whenDuplicateKeyReturnsOtherSession() {
        Long memberId = 1L;
        ChatSession existingSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(existingSession, "id", 77L);
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingSession));
        when(chatSessionRepository.saveAndFlush(any(ChatSession.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1' for key 'uk_chat_sessions_active_member_id'"));

        chatSessionService.getOrCreateActiveSession(memberId);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 시간이 지난 상담을 만료시키고 새로 만들면 EXPIRED, ACTIVE 순으로 발행한다")
    void publishesExpiredThenActive_whenTimedOutSessionIsReplaced() {
        Long memberId = 1L;
        ChatSession expiredSession = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(expiredSession, "id", 50L);
        ReflectionTestUtils.setField(expiredSession, "lastMessageAt", LocalDateTime.now().minusMinutes(35));
        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.of(expiredSession));
        when(chatSessionRepository.findById(50L)).thenReturn(Optional.of(expiredSession));
        when(chatSessionRepository.saveAndFlush(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 200L);
            }
            return saved;
        });

        chatSessionService.getOrCreateActiveSession(memberId);

        InOrder order = inOrder(eventPublisher);
        order.verify(eventPublisher).publishEvent(new ChatSessionChangedEvent(memberId, 50L, ChatSessionStatus.EXPIRED));
        order.verify(eventPublisher).publishEvent(new ChatSessionChangedEvent(memberId, 200L, ChatSessionStatus.ACTIVE));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 실제로 만료시켰을 때만 EXPIRED로 발행하고, 이미 끝난 상담이면 발행하지 않는다")
    void publishesExpiredOnly_whenActuallyExpired() {
        ChatSession active = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(active, "id", 10L);
        ChatSession completed = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(completed, "id", 11L);
        completed.complete();
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(active));
        when(chatSessionRepository.findById(11L)).thenReturn(Optional.of(completed));

        chatSessionService.expireSession(10L);
        chatSessionService.expireSession(11L);
        chatSessionService.expireSession(12L);

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(eventPublisher).publishEvent(new ChatSessionChangedEvent(1L, 10L, ChatSessionStatus.EXPIRED));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 상담을 종료하면 COMPLETED로 발행하고, 종료에 실패하면 발행하지 않는다")
    void publishesCompleted_whenSessionIsCompleted() {
        ChatSession session = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(session, "id", 10L);
        when(chatSessionRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(session));

        chatSessionService.completeSession(1L, 10L);
        assertThatThrownBy(() -> chatSessionService.completeSession(1L, 10L))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(eventPublisher).publishEvent(new ChatSessionChangedEvent(1L, 10L, ChatSessionStatus.COMPLETED));
    }

    @Test
    @DisplayName("상담 바뀜 이벤트: 요리 완료로 진행 중 상담을 끝내면 COMPLETED로 발행하고, 이미 만료된 상담은 발행하지 않는다")
    void publishesCompletedOnly_whenCookingCompletesActiveSession() {
        ChatSession active = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(active, "id", 10L);
        ChatSession expired = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(expired, "id", 11L);
        expired.expire();
        when(chatSessionRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(active));
        when(chatSessionRepository.findByIdAndMemberId(11L, 1L)).thenReturn(Optional.of(expired));

        chatSessionService.completeSessionFromCooking(1L, 10L);
        chatSessionService.completeSessionFromCooking(1L, 11L);

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(eventPublisher).publishEvent(new ChatSessionChangedEvent(1L, 10L, ChatSessionStatus.COMPLETED));
    }

    @Test
    @DisplayName("요리 완료로 끝낼 상담이 없거나 이미 끝났으면 예외 없이 건너뛴다")
    void skipsWithoutThrowing_whenCookingSessionIsMissingOrEnded() {
        ChatSession completed = session(11L);
        completed.complete();
        when(chatSessionRepository.findByIdAndMemberId(11L, 1L)).thenReturn(Optional.of(completed));
        when(chatSessionRepository.findByIdAndMemberId(12L, 1L)).thenReturn(Optional.empty());

        chatSessionService.completeSessionFromCooking(1L, 11L);
        chatSessionService.completeSessionFromCooking(1L, 12L);

        assertThat(completed.getStatus()).isEqualTo(ChatSessionStatus.COMPLETED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("요청에 상담이 없으면 진행 중 상담에서 이 레시피를 추천한 적 있을 때 그 상담을 잇는다")
    void returnsActiveSession_whenRecipeWasRecommended() {
        when(chatSessionRepository.findByMemberIdAndStatus(1L, ChatSessionStatus.ACTIVE)).thenReturn(Optional.of(session(10L)));
        when(chatRecommendationRepository.existsRecipeInSession(10L, 12L)).thenReturn(true);

        assertThat(chatSessionService.findCookingSessionId(1L, null, 12L)).contains(10L);
    }

    @Test
    @DisplayName("요청에 상담이 없고 진행 중 상담에서 이 레시피를 추천하지 않았으면 잇지 않는다")
    void returnsEmpty_whenRecipeWasNotRecommended() {
        when(chatSessionRepository.findByMemberIdAndStatus(1L, ChatSessionStatus.ACTIVE)).thenReturn(Optional.of(session(10L)));
        when(chatRecommendationRepository.existsRecipeInSession(10L, 12L)).thenReturn(false);

        assertThat(chatSessionService.findCookingSessionId(1L, null, 12L)).isEmpty();
    }

    @Test
    @DisplayName("진행 중 상담이 없으면 잇지 않는다")
    void returnsEmpty_whenNoActiveSession() {
        when(chatSessionRepository.findByMemberIdAndStatus(1L, ChatSessionStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(chatSessionService.findCookingSessionId(1L, null, 12L)).isEmpty();
        verify(chatRecommendationRepository, never()).existsRecipeInSession(any(), any());
    }

    @Test
    @DisplayName("30분이 지나 아직 ACTIVE인 상담은 추천했어도 잇지 않는다")
    void returnsEmpty_whenActiveSessionTimedOut() {
        ChatSession idle = session(10L);
        ReflectionTestUtils.setField(idle, "lastMessageAt", LocalDateTime.now().minusMinutes(35));
        when(chatSessionRepository.findByMemberIdAndStatus(1L, ChatSessionStatus.ACTIVE)).thenReturn(Optional.of(idle));
        when(chatSessionRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(idle));

        assertThat(chatSessionService.findCookingSessionId(1L, null, 12L)).isEmpty();
        assertThat(chatSessionService.findCookingSessionId(1L, 10L, 12L)).isEmpty();
        verify(chatRecommendationRepository, never()).existsRecipeInSession(any(), any());
    }

    @Test
    @DisplayName("요청에 담긴 상담이 내 진행 중 상담이면 추천 여부를 보지 않고 그 상담을 잇는다")
    void returnsRequestedSession_whenItIsMyActiveSession() {
        when(chatSessionRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(session(10L)));

        assertThat(chatSessionService.findCookingSessionId(1L, 10L, 12L)).contains(10L);
        verify(chatRecommendationRepository, never()).existsRecipeInSession(any(), any());
    }

    @Test
    @DisplayName("요청에 담긴 상담이 남의 상담·끝난 상담·만료된 상담이면 예외 없이 잇지 않는다")
    void returnsEmpty_whenRequestedSessionIsOthersOrEnded() {
        ChatSession completed = session(11L);
        completed.complete();
        ChatSession expired = session(12L);
        expired.expire();
        when(chatSessionRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.empty());
        when(chatSessionRepository.findByIdAndMemberId(11L, 1L)).thenReturn(Optional.of(completed));
        when(chatSessionRepository.findByIdAndMemberId(12L, 1L)).thenReturn(Optional.of(expired));

        assertThat(chatSessionService.findCookingSessionId(1L, 10L, 12L)).isEmpty();
        assertThat(chatSessionService.findCookingSessionId(1L, 11L, 12L)).isEmpty();
        assertThat(chatSessionService.findCookingSessionId(1L, 12L, 12L)).isEmpty();
    }

    private static ChatSession session(Long id) {
        ChatSession session = ChatSession.builder().memberId(1L).build();
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }
}
