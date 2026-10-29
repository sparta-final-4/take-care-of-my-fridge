package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private ChatProperties chatProperties;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("saveExchange: 메시지가 없을 때 저장하면 sequence 1, 2로 저장되고 세션의 lastMessageAt이 갱신된다")
    void saveExchangeFirstTimeAssignsSequence1And2AndTouchesSession() {
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(1L).build();
        LocalDateTime beforeTime = LocalDateTime.now().minusHours(1);
        ReflectionTestUtils.setField(session, "lastMessageAt", beforeTime);

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMaxSequenceBySessionId(sessionId)).thenReturn(Optional.empty());

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            ReflectionTestUtils.setField(msg, "id", (long) msg.getSequence());
            return msg;
        });

        List<ChatMessageResponse> result = chatMessageService.saveExchange(
                sessionId,
                "안녕하세요 레시피 추천해줘",
                "아직 AI 연결 전이에요. 받은 메시지: 안녕하세요 레시피 추천해줘"
        );

        assertThat(result).hasSize(2);
        ChatMessageResponse userMsg = result.get(0);
        ChatMessageResponse assistantMsg = result.get(1);

        assertThat(userMsg.sequence()).isEqualTo(1);
        assertThat(userMsg.role()).isEqualTo(ChatMessageRole.USER);
        assertThat(userMsg.content()).isEqualTo("안녕하세요 레시피 추천해줘");

        assertThat(assistantMsg.sequence()).isEqualTo(2);
        assertThat(assistantMsg.role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(assistantMsg.content()).contains("아직 AI 연결 전이에요");

        // lastMessageAt이 갱신되었는지 검증
        assertThat(session.getLastMessageAt()).isAfter(beforeTime);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        List<ChatMessage> savedList = captor.getAllValues();
        assertThat(savedList.get(0).getSequence()).isEqualTo(1);
        assertThat(savedList.get(1).getSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("saveExchange: 기존 마지막 sequence가 2이면 다음 메시지는 sequence 3, 4로 저장된다")
    void saveExchangeSecondTimeAssignsSequence3And4() {
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(1L).build();

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMaxSequenceBySessionId(sessionId)).thenReturn(Optional.of(2));

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        List<ChatMessageResponse> result = chatMessageService.saveExchange(
                sessionId,
                "두 번째 질문",
                "두 번째 답변"
        );

        assertThat(result.get(0).sequence()).isEqualTo(3);
        assertThat(result.get(0).role()).isEqualTo(ChatMessageRole.USER);

        assertThat(result.get(1).sequence()).isEqualTo(4);
        assertThat(result.get(1).role()).isEqualTo(ChatMessageRole.ASSISTANT);
    }

    @Test
    @DisplayName("saveExchange: 검증 후 저장 전에 세션이 COMPLETED가 되면 메시지를 저장하지 않고 409 예외를 던진다")
    void saveExchangeThrows409WhenSessionCompletedBeforeSave() {
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(1L).build();
        session.complete(); // 답을 만드는 동안 세션이 완료됨

        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatMessageService.saveExchange(sessionId, "사용자 메시지", "답변 메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);

        // 메시지 저장이 전혀 호출되지 않았는지 검증
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateSessionForSending: 없거나 다른 회원의 세션이면 404 CHAT_ROOM_NOT_FOUND 예외가 발생한다")
    void validateSessionForSendingThrows404WhenNotFoundOrOtherMember() {
        Long memberId = 1L;
        Long sessionId = 999L;
        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.validateSessionForSending(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("validateSessionForSending: 30분 이내 활성 세션은 정상적으로 검증을 통과한다")
    void validateSessionForSendingPassesWhenActiveAndNotExpired() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.of(session));
        when(chatProperties.sessionTimeout()).thenReturn(Duration.ofMinutes(30));

        chatMessageService.validateSessionForSending(memberId, sessionId);

        verify(chatSessionService, never()).expireSession(any());
    }

    @Test
    @DisplayName("validateSessionForSending: 마지막 대화로부터 30분이 지난 세션은 expireSession을 호출해 만료시키고 CHAT_SESSION_EXPIRED 예외를 던진다")
    void validateSessionForSendingExpiresSessionAndThrows409WhenExpired() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(session, "lastMessageAt", LocalDateTime.now().minusMinutes(31));

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.of(session));
        when(chatProperties.sessionTimeout()).thenReturn(Duration.ofMinutes(30));

        assertThatThrownBy(() -> chatMessageService.validateSessionForSending(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SESSION_EXPIRED);

        verify(chatSessionService, times(1)).expireSession(sessionId);
    }

    @Test
    @DisplayName("validateSessionForSending: 이미 종료(COMPLETED)된 세션에 메시지를 보내면 409 CHAT_ROOM_ALREADY_COMPLETED 예외가 발생한다")
    void validateSessionForSendingThrows409WhenSessionCompleted() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        session.complete();

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatMessageService.validateSessionForSending(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("validateSessionForSending: 만료(EXPIRED)된 세션에 메시지를 보내면 409 CHAT_ROOM_ALREADY_COMPLETED 예외가 발생한다")
    void validateSessionForSendingThrows409WhenSessionExpired() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        session.expire();

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatMessageService.validateSessionForSending(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("getMessages: 종료된 세션도 메시지 목록을 정상적으로 조회할 수 있다")
    void getMessagesReturnsMessagesEvenIfSessionCompleted() {
        Long memberId = 1L;
        Long sessionId = 10L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        session.complete();

        ChatMessage msg1 = ChatMessage.builder()
                .session(session)
                .sequence(1)
                .role(ChatMessageRole.USER)
                .content("질문")
                .build();
        ChatMessage msg2 = ChatMessage.builder()
                .session(session)
                .sequence(2)
                .role(ChatMessageRole.ASSISTANT)
                .content("답변")
                .build();

        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderBySequenceAsc(sessionId)).thenReturn(List.of(msg1, msg2));

        List<ChatMessageResponse> messages = chatMessageService.getMessages(memberId, sessionId);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).sequence()).isEqualTo(1);
        assertThat(messages.get(1).sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("getMessages: 남의 세션이거나 존재하지 않는 세션이면 404 CHAT_ROOM_NOT_FOUND 예외가 발생한다")
    void getMessagesThrows404WhenNotFound() {
        Long memberId = 1L;
        Long sessionId = 999L;
        when(chatSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getMessages(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }
}
