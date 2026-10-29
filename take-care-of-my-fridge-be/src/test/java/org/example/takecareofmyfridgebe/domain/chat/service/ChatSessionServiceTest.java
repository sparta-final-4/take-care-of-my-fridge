package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private ChatProperties chatProperties;

    @InjectMocks
    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        lenient().when(chatProperties.sessionTimeout()).thenReturn(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("진행 중인 세션이 없으면 새로 만들어 반환한다")
    void createsNewSessionWhenNoActiveSessionExists() {
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
    void returnsExistingSessionWhenActiveSessionExists() {
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
    void getOrCreateActiveSessionExpiresExistingAndCreatesNewWhenExpired() {
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
    void expireSessionMarksActiveSessionAsExpiredAndFlushes() {
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
    void getSessionReturnsSession() {
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
    void getSessionThrowsNotFoundWhenSessionNotExistsOrBelongsToOthers() {
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
    void completeSessionThrowsNotFoundWhenSessionNotExistsOrBelongsToOthers() {
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
    void completeSessionChangesStatusToCompleted() {
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
    void completeSessionThrowsConflictWhenSessionAlreadyCompleted() {
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
    void completeSessionThrowsConflictWhenSessionExpired() {
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
    void createsNewSessionAfterPreviousSessionCompleted() {
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
    void getOrCreateActiveSessionReturnsExistingSessionWhenDuplicateKeyOccurs() {
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
    void getOrCreateActiveSessionRethrowsExceptionWhenOtherConstraintFails() {
        Long memberId = 1L;

        when(chatSessionRepository.findByMemberIdAndStatus(memberId, ChatSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(chatSessionRepository.saveAndFlush(any(ChatSession.class)))
                .thenThrow(new DataIntegrityViolationException("Cannot add or update a child row: foreign key constraint fails"));

        assertThatThrownBy(() -> chatSessionService.getOrCreateActiveSession(memberId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key constraint fails");
    }
}
