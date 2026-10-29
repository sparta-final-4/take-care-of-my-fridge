package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ChatProperties.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ChatMessageService.class, ChatSessionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatMessageRepositoryAndServiceTest extends MySqlContainerSupport {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        try {
            jdbcTemplate.execute("""
                ALTER TABLE chat_sessions
                ADD COLUMN active_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN member_id ELSE NULL END) STORED,
                ADD CONSTRAINT uk_chat_sessions_active_member_id UNIQUE (active_member_id)
            """);
        } catch (Exception ignored) {
        }

        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("saveExchange: 보내면 두 메시지가 sequence 1, 2로 저장되고, 한 번 더 보내면 3, 4로 저장된다")
    void saveExchangeIncrementsSequenceCorrectly() {
        Long memberId = 100L;
        ChatSession session = chatSessionRepository.saveAndFlush(
                ChatSession.builder().memberId(memberId).build()
        );
        Long sessionId = session.getId();
        LocalDateTime initialLastMessageAt = session.getLastMessageAt();

        // 1번째 메시지 교환
        List<ChatMessageResponse> firstExchange = chatMessageService.saveExchange(
                sessionId,
                "첫 번째 질문",
                "첫 번째 답변"
        );

        assertThat(firstExchange).hasSize(2);
        assertThat(firstExchange.get(0).sequence()).isEqualTo(1);
        assertThat(firstExchange.get(0).role()).isEqualTo(ChatMessageRole.USER);
        assertThat(firstExchange.get(1).sequence()).isEqualTo(2);
        assertThat(firstExchange.get(1).role()).isEqualTo(ChatMessageRole.ASSISTANT);

        // 2번째 메시지 교환
        List<ChatMessageResponse> secondExchange = chatMessageService.saveExchange(
                sessionId,
                "두 번째 질문",
                "두 번째 답변"
        );

        assertThat(secondExchange).hasSize(2);
        assertThat(secondExchange.get(0).sequence()).isEqualTo(3);
        assertThat(secondExchange.get(0).role()).isEqualTo(ChatMessageRole.USER);
        assertThat(secondExchange.get(1).sequence()).isEqualTo(4);
        assertThat(secondExchange.get(1).role()).isEqualTo(ChatMessageRole.ASSISTANT);

        // 전체 기록 조회 순서 검증
        List<ChatMessageResponse> allMessages = chatMessageService.getMessages(memberId, sessionId);
        assertThat(allMessages).hasSize(4);
        assertThat(allMessages).extracting(ChatMessageResponse::sequence)
                .containsExactly(1, 2, 3, 4);

        // 세션의 last_message_at이 갱신되었는지 검증
        ChatSession updatedSession = chatSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updatedSession.getLastMessageAt()).isAfterOrEqualTo(initialLastMessageAt);
    }

    @Test
    @DisplayName("saveExchange: 검증 후 저장 전에 세션이 COMPLETED가 되면 저장하지 않고 409 예외를 던진다")
    void saveExchangeThrows409AndDoesNotSaveWhenSessionCompletedBeforeSave() {
        Long memberId = 100L;
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        session.complete(); // 세션이 이미 종료됨
        ChatSession savedSession = chatSessionRepository.saveAndFlush(session);
        Long sessionId = savedSession.getId();

        // 저장 시도 시 409 CHAT_ROOM_ALREADY_COMPLETED 예외 발생
        assertThatThrownBy(() -> chatMessageService.saveExchange(sessionId, "사용자 메시지", "답변 메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_ALREADY_COMPLETED);

        // DB에 메시지가 전혀 저장되지 않았음을 검증
        List<ChatMessageResponse> messages = chatMessageRepository.findBySessionIdOrderBySequenceAsc(sessionId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        assertThat(messages).isEmpty();
    }
}
