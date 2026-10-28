package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatSessionConcurrencyTest extends MySqlContainerSupport {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

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
            // 이미 컬럼 및 제약이 존재하는 경우 통과
        }

        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("같은 회원으로 두 스레드가 동시에 getOrCreateActiveSession을 호출하면 둘 다 예외 없이 같은 세션 ID를 받고 DB의 ACTIVE 세션은 1개이다")
    void concurrentGetOrCreateActiveSessionReturnsSameSessionAndCreatesOnlyOne() throws Exception {
        Long memberId = 1L;
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
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
        executor.shutdown();

        // 1. 둘 다 예외 없이 같은 세션 ID를 받음
        assertThat(responses).hasSize(2);
        ChatSessionResponse first = responses.get(0);
        ChatSessionResponse second = responses.get(1);

        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isNotNull();
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.status()).isEqualTo(ChatSessionStatus.ACTIVE);
        assertThat(second.status()).isEqualTo(ChatSessionStatus.ACTIVE);

        // 2. DB에 존재하는 ACTIVE 세션은 1개
        List<ChatSession> activeSessions = chatSessionRepository.findAll().stream()
                .filter(session -> session.getMemberId().equals(memberId) && session.getStatus() == ChatSessionStatus.ACTIVE)
                .toList();
        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.get(0).getId()).isEqualTo(first.id());
    }
}
