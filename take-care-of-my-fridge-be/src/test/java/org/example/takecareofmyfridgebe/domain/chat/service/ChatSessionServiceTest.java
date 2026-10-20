package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ChatSessionService 단위 테스트.
 * 회원 소유 상담 세션 검증(타인 세션 시 404),
 * 요리 완료 시 진행 중(ACTIVE) 세션의 종료(COMPLETED) 전이,
 * 이미 종료(COMPLETED)된 세션 검증 시 409 발생,
 * 만료(EXPIRED) 세션에 대한 종료 건너뜀 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @InjectMocks
    private ChatSessionService chatSessionService;

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long SESSION_ID = 10L;

    /**
     * 다른 회원의 상담 세션 ID로 조회하면 404 CHAT_ROOM_NOT_FOUND 예외가 발생한다.
     */
    @Test
    void validateAndGetSessionThrowsWhenSessionBelongsToAnotherMember() {
        // Given: 다른 회원 세션 조회 시 빈 결과 반환
        when(chatSessionRepository.findByIdAndMemberId(SESSION_ID, OTHER_MEMBER_ID))
                .thenReturn(Optional.empty());

        // When & Then: 404 예외 발생 검증
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chatSessionService.validateAndGetSession(OTHER_MEMBER_ID, SESSION_ID)
        );
        assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getErrorCode().getStatus());
    }

    /**
     * 진행 중(ACTIVE)인 상담 세션은 completeSessionFromCooking 호출 시 COMPLETED로 상태가 변경된다.
     */
    @Test
    void completeSessionFromCookingCompletesActiveSession() {
        // Given: ACTIVE 상태의 세션 생성 및 mock 설정
        ChatSession session = createSession(MEMBER_ID, SESSION_ID, ChatSessionStatus.ACTIVE);
        when(chatSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        // When: 요리 완료에 따른 세션 종료 호출
        chatSessionService.completeSessionFromCooking(MEMBER_ID, SESSION_ID);

        // Then: 상태가 COMPLETED로 변경됨
        assertEquals(ChatSessionStatus.COMPLETED, session.getStatus());
        assertTrue(session.isCompleted());
    }

    /**
     * 이미 종료(COMPLETED)된 상담 세션으로 검증 시 409 CHAT_ROOM_ALREADY_COMPLETED 예외가 발생한다.
     */
    @Test
    void validateAndGetSessionThrowsWhenSessionIsAlreadyCompleted() {
        // Given: COMPLETED 상태의 세션 생성 및 mock 설정
        ChatSession session = createSession(MEMBER_ID, SESSION_ID, ChatSessionStatus.COMPLETED);
        when(chatSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        // When & Then: 409 예외 발생 검증
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chatSessionService.validateAndGetSession(MEMBER_ID, SESSION_ID)
        );
        assertEquals(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED, exception.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getErrorCode().getStatus());
    }

    /**
     * 만료(EXPIRED)된 상담 세션은 completeSessionFromCooking 호출 시 종료 처리를 건너뛰고 EXPIRED 상태를 유지한다.
     */
    @Test
    void completeSessionFromCookingSkipsCompletionWhenSessionIsExpired() {
        // Given: EXPIRED 상태의 세션 생성 및 mock 설정
        ChatSession session = createSession(MEMBER_ID, SESSION_ID, ChatSessionStatus.EXPIRED);
        when(chatSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        // When: 요리 완료에 따른 세션 종료 호출
        chatSessionService.completeSessionFromCooking(MEMBER_ID, SESSION_ID);

        // Then: 상태가 변경되지 않고 EXPIRED 유지
        assertEquals(ChatSessionStatus.EXPIRED, session.getStatus());
        assertFalse(session.isActive());
    }

    private ChatSession createSession(Long memberId, Long sessionId, ChatSessionStatus status) {
        ChatSession session = ChatSession.builder()
                .memberId(memberId)
                .build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        ReflectionTestUtils.setField(session, "status", status);
        return session;
    }
}
