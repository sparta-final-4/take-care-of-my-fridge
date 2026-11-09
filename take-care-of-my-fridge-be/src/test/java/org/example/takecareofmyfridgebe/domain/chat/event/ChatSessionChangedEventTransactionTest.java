package org.example.takecareofmyfridgebe.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.push.ChatSessionPushPublisher;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계대로 커밋된 뒤에만 Redis 발행기가 불리는지 본다.
// 발행기는 mock으로 바꿔, 리스너가 언제 몇 번 무엇을 넘기는지만 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ChatProperties.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ChatSessionService.class, ChatMessageService.class,
        ChatSessionChangedEventListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatSessionChangedEventTransactionTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 900L;

    @MockitoBean
    private ChatSessionPushPublisher chatSessionPushPublisher;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatSessionRepository chatSessionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ChatSessionChangedEventTransactionTest(
            ChatSessionService chatSessionService,
            ChatMessageService chatMessageService,
            ChatSessionRepository chatSessionRepository,
            DataSource dataSource) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.chatSessionRepository = chatSessionRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM chat_recommendations");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("상담 종료가 커밋되면 COMPLETED 신호를 한 번 발행한다")
    void publishesCompleted_whenCompleteIsCommitted() {
        Long sessionId = saveActiveSession();

        chatSessionService.completeSession(MEMBER_ID, sessionId);

        verify(chatSessionPushPublisher).publish(MEMBER_ID, sessionId, ChatSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("답 저장이 커밋되면 ACTIVE 신호를 발행한다")
    void publishesActive_whenExchangeIsCommitted() {
        Long sessionId = saveActiveSession();

        chatMessageService.saveExchange(sessionId, "질문", "답");

        verify(chatSessionPushPublisher).publish(MEMBER_ID, sessionId, ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("트랜잭션이 없는 상담 시작 경로에서도 새로 만들면 바로 발행하고, 진행 중 상담을 그대로 돌려줄 때는 발행하지 않는다")
    void publishesOnlyWhenCreated_whenStartingWithoutTransaction() {
        ChatSessionResponse created = chatSessionService.getOrCreateActiveSession(MEMBER_ID);
        verify(chatSessionPushPublisher).publish(MEMBER_ID, created.id(), ChatSessionStatus.ACTIVE);
        clearInvocations(chatSessionPushPublisher);

        ChatSessionResponse existing = chatSessionService.getOrCreateActiveSession(MEMBER_ID);

        assertThat(existing.id()).isEqualTo(created.id());
        verifyNoInteractions(chatSessionPushPublisher);
    }

    @Test
    @DisplayName("상담 시작 때 시간이 지난 상담이 있으면 EXPIRED, 새 상담 ACTIVE 순으로 발행한다")
    void publishesExpiredThenActive_whenStartingWithExpiredSession() {
        Long oldSessionId = saveActiveSession();
        makeIdle(oldSessionId);

        ChatSessionResponse created = chatSessionService.getOrCreateActiveSession(MEMBER_ID);

        InOrder order = inOrder(chatSessionPushPublisher);
        order.verify(chatSessionPushPublisher).publish(MEMBER_ID, oldSessionId, ChatSessionStatus.EXPIRED);
        order.verify(chatSessionPushPublisher).publish(MEMBER_ID, created.id(), ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("보내기 검증에서 만료시키면 바깥 요청이 실패해도 만료는 커밋되어 EXPIRED 신호를 발행한다")
    void publishesExpired_whenSendingToTimedOutSession() {
        Long sessionId = saveActiveSession();
        makeIdle(sessionId);

        assertThatThrownBy(() -> chatMessageService.validateSessionForSending(MEMBER_ID, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SESSION_EXPIRED);

        verify(chatSessionPushPublisher).publish(MEMBER_ID, sessionId, ChatSessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 끝난 상담을 종료하려다 실패하면 발행하지 않는다")
    void doesNotPublish_whenCompleteFails() {
        Long sessionId = saveActiveSession();
        chatSessionService.completeSession(MEMBER_ID, sessionId);
        clearInvocations(chatSessionPushPublisher);

        assertThatThrownBy(() -> chatSessionService.completeSession(MEMBER_ID, sessionId))
                .isInstanceOf(BusinessException.class);

        verify(chatSessionPushPublisher, never()).publish(any(), any(), any());
    }

    private Long saveActiveSession() {
        return chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(MEMBER_ID).build()).getId();
    }

    private void makeIdle(Long sessionId) {
        jdbcTemplate.update("UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(35), sessionId);
    }
}
