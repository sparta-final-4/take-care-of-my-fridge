package org.example.takecareofmyfridgebe.domain.chat.repository;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ChatProperties.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ChatSessionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatSessionUniqueConstraintTest extends MySqlContainerSupport {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchemaAndData() {
        // 실제 MySQL chat_sessions 테이블에 active_member_id 계산 컬럼과 유니크 제약이 없으면 추가
        try {
            jdbcTemplate.execute("""
                ALTER TABLE chat_sessions
                ADD COLUMN active_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN member_id ELSE NULL END) STORED,
                ADD CONSTRAINT uk_chat_sessions_active_member_id UNIQUE (active_member_id)
            """);
        } catch (Exception ignored) {
            // 이미 추가되어 있는 경우 통과
        }

        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("같은 회원의 ACTIVE 세션을 두 번 저장하면 두 번째는 유니크 제약 위반(DataIntegrityViolationException)이 발생한다")
    void savingTwoActiveSessionsForSameMemberThrowsUniqueConstraintViolation() {
        Long memberId = 100L;

        // 첫 번째 ACTIVE 세션 저장 성공
        ChatSession session1 = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session1);

        // 동일 회원의 두 번째 ACTIVE 세션 저장 시도 -> 유니크 제약 위반
        ChatSession session2 = ChatSession.builder().memberId(memberId).build();
        assertThatThrownBy(() -> chatSessionRepository.saveAndFlush(session2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_chat_sessions_active_member_id");
    }

    @Test
    @DisplayName("기존 ACTIVE 세션을 COMPLETED로 변경하면 동일 회원의 새로운 ACTIVE 세션을 저장할 수 있다")
    void completedSessionAllowsNewActiveSessionForSameMember() {
        Long memberId = 100L;

        // 첫 번째 세션 생성 및 종료(COMPLETED)
        ChatSession session1 = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session1);
        session1.complete();
        chatSessionRepository.saveAndFlush(session1);

        // 동일 회원의 두 번째 ACTIVE 세션 저장 -> 성공
        ChatSession session2 = ChatSession.builder().memberId(memberId).build();
        ChatSession saved2 = chatSessionRepository.saveAndFlush(session2);

        assertThat(saved2.getId()).isNotNull();
        assertThat(saved2.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("COMPLETED나 EXPIRED 상태의 세션은 동일 회원이라도 여러 개 존재할 수 있다")
    void multipleCompletedAndExpiredSessionsCanExistForSameMember() {
        Long memberId = 100L;

        // COMPLETED 세션 2개
        ChatSession session1 = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session1);
        session1.complete();
        chatSessionRepository.saveAndFlush(session1);

        ChatSession session2 = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session2);
        session2.complete();
        chatSessionRepository.saveAndFlush(session2);

        // EXPIRED 세션 2개
        ChatSession session3 = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session3);
        session3.expire();
        chatSessionRepository.saveAndFlush(session3);

        ChatSession session4 = ChatSession.builder().memberId(memberId).build();
        chatSessionRepository.saveAndFlush(session4);
        session4.expire();
        chatSessionRepository.saveAndFlush(session4);

        // 4개 모두 정상 저장되어 조회됨
        List<ChatSession> sessions = chatSessionRepository.findAll();
        assertThat(sessions).hasSize(4);
    }

    @Test
    @DisplayName("이미 진행 중이면 같은 세션 반환")
    void getOrCreateActiveSessionReturnsSameSessionWhenAlreadyActive() {
        Long memberId = 100L;

        // 첫 번째 호출: 세션 신규 생성
        ChatSessionResponse firstResponse = chatSessionService.getOrCreateActiveSession(memberId);
        assertThat(firstResponse.status()).isEqualTo(ChatSessionStatus.ACTIVE);

        // 두 번째 호출: 이미 ACTIVE 세션이 있으므로 기존 세션 반환
        ChatSessionResponse secondResponse = chatSessionService.getOrCreateActiveSession(memberId);
        assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        assertThat(secondResponse.status()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("같은 회원으로 두 스레드가 동시에 getOrCreateActiveSession 호출 시 둘 다 같은 세션 ID를 받고 DB의 ACTIVE 세션은 1개이다 (10회 반복)")
    void concurrentGetOrCreateActiveSessionReturnsSameSessionAndCreatesOnlyOne() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 10; i++) {
                jdbcTemplate.execute("DELETE FROM chat_sessions");
                Long memberId = 200L + i;

                CountDownLatch readyLatch = new CountDownLatch(2);
                CountDownLatch startLatch = new CountDownLatch(1);

                List<Callable<ChatSessionResponse>> tasks = List.of(
                        () -> {
                            readyLatch.countDown();
                            startLatch.await();
                            return chatSessionService.getOrCreateActiveSession(memberId);
                        },
                        () -> {
                            readyLatch.countDown();
                            startLatch.await();
                            return chatSessionService.getOrCreateActiveSession(memberId);
                        }
                );

                List<Future<ChatSessionResponse>> futures = new ArrayList<>();
                for (Callable<ChatSessionResponse> task : tasks) {
                    futures.add(executor.submit(task));
                }

                readyLatch.await(5, TimeUnit.SECONDS);
                startLatch.countDown();

                List<ChatSessionResponse> responses = new ArrayList<>();
                for (Future<ChatSessionResponse> future : futures) {
                    responses.add(future.get(10, TimeUnit.SECONDS));
                }

                // 1. 둘 다 예외 없이 끝나고, 같은 세션 id를 받음
                assertThat(responses).hasSize(2);
                ChatSessionResponse first = responses.get(0);
                ChatSessionResponse second = responses.get(1);

                assertThat(first.id()).isNotNull();
                assertThat(second.id()).isNotNull();
                assertThat(first.id())
                        .as("%d번째 반복에서 두 스레드가 서로 다른 세션 ID를 받았습니다", i + 1)
                        .isEqualTo(second.id());
                assertThat(first.status()).isEqualTo(ChatSessionStatus.ACTIVE);
                assertThat(second.status()).isEqualTo(ChatSessionStatus.ACTIVE);

                // 2. DB의 ACTIVE 세션은 1개
                List<ChatSession> activeSessions = chatSessionRepository.findAll().stream()
                        .filter(s -> s.getMemberId().equals(memberId) && s.getStatus() == ChatSessionStatus.ACTIVE)
                        .toList();
                assertThat(activeSessions)
                        .as("%d번째 반복에서 DB의 ACTIVE 세션 수가 1개가 아닙니다", i + 1)
                        .hasSize(1);
                assertThat(activeSessions.get(0).getId()).isEqualTo(first.id());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
