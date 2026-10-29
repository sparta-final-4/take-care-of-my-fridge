package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ChatProperties.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ChatSessionService.class, ChatMessageService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatSessionExpiryIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchemaAndData() {
        try {
            jdbcTemplate.execute("""
                ALTER TABLE chat_sessions
                ADD COLUMN active_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN member_id ELSE NULL END) STORED,
                ADD CONSTRAINT uk_chat_sessions_active_member_id UNIQUE (active_member_id)
            """);
        } catch (Exception ignored) {
            // 이미 추가되어 있는 경우 무시
        }

        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("30분 지난 세션에 메시지 전송 시 409 CHAT_SESSION_EXPIRED가 발생하고 DB에 실제로 EXPIRED로 저장된다")
    void sendingMessageToSessionOlderThan30MinutesExpiresSessionInDbAndThrowsExpiredError() {
        Long memberId = 100L;

        // 1. ACTIVE 세션 생성 및 저장
        ChatSession session = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session);
        Long sessionId = session.getId();

        // 2. last_message_at을 35분 전으로 강제 변경
        jdbcTemplate.update(
                "UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(35),
                sessionId
        );

        // 3. 메시지 전송 전 세션 검증 시 만료 예외 발생
        assertThatThrownBy(() -> chatMessageService.validateSessionForSending(memberId, sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SESSION_EXPIRED);

        // 4. 예외 발생 후에도 DB에 실제로 EXPIRED 상태가 롤백되지 않고 커밋되어 저장되었는지 확인
        ChatSession updatedSession = chatSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updatedSession.getStatus()).isEqualTo(ChatSessionStatus.EXPIRED);
        assertThat(updatedSession.isActive()).isFalse();
    }

    @Test
    @DisplayName("30분 지난 세션이 있을 때 상담 시작(getOrCreateActiveSession) 시 기존 세션은 EXPIRED로 바뀌고 유니크 제약 오류 없이 새 ACTIVE 세션을 반환한다")
    void startingChatWithExpiredSessionMarksOldExpiredAndCreatesNewActiveSession() {
        Long memberId = 200L;

        // 1. 기존 ACTIVE 세션 생성 및 저장
        ChatSession oldSession = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(oldSession);
        Long oldSessionId = oldSession.getId();

        // 2. last_message_at을 35분 전으로 변경
        jdbcTemplate.update(
                "UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(35),
                oldSessionId
        );

        // 3. 상담 시작 요청 시 기존 세션이 만료되고 새 ACTIVE 세션 생성
        ChatSessionResponse newSessionResponse = chatSessionService.getOrCreateActiveSession(memberId);

        // 4. 검증: 새로운 세션 ID가 발급되고 ACTIVE 상태임
        assertThat(newSessionResponse.id()).isNotNull().isNotEqualTo(oldSessionId);
        assertThat(newSessionResponse.status()).isEqualTo(ChatSessionStatus.ACTIVE);

        // 5. 검증: 기존 세션은 DB에서 실제로 EXPIRED로 변경됨
        ChatSession dbOldSession = chatSessionRepository.findById(oldSessionId).orElseThrow();
        assertThat(dbOldSession.getStatus()).isEqualTo(ChatSessionStatus.EXPIRED);

        // 6. 검증: DB 내 ACTIVE 상태인 세션은 새로 만든 세션 1개뿐이어야 함
        long activeCount = chatSessionRepository.findAll().stream()
                .filter(s -> s.getMemberId().equals(memberId) && s.getStatus() == ChatSessionStatus.ACTIVE)
                .count();
        assertThat(activeCount).isEqualTo(1L);
    }
}
