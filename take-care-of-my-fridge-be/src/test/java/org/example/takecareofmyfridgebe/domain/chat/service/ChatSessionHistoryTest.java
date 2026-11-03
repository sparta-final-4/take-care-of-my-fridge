package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionListStatus;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionStatusCounts;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSessionSummaryResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ChatProperties.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ChatSessionService.class, ChatMessageService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatSessionHistoryTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 같은 DB를 쓰는 다른 테스트(chat_sessions만 지움)가 남은 메시지의 외래 키에 막히지 않게 앞뒤로 비운다
    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("상담 목록은 최근 시작순이고, 메시지 수와 첫 사용자 질문을 함께 준다")
    void listsRecentFirstWithStats() {
        ChatSession older = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(older, 1, ChatMessageRole.USER, "오래된 질문");
        addMessage(older, 2, ChatMessageRole.ASSISTANT, "답변");
        addMessage(older, 3, ChatMessageRole.USER, "두 번째 질문");
        addMessage(older, 4, ChatMessageRole.ASSISTANT, "답변");

        ChatSession newer = session(MEMBER_ID, LocalDateTime.of(2026, 10, 2, 9, 0));
        addMessage(newer, 1, ChatMessageRole.USER, "최근 질문");
        addMessage(newer, 2, ChatMessageRole.ASSISTANT, "답변");

        PageResponse<ChatSessionSummaryResponse> page =
                list(null, null, null);

        assertThat(page.content()).extracting(ChatSessionSummaryResponse::sessionId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(page.content().get(0).status()).isEqualTo(ChatSessionStatus.ACTIVE);
        assertThat(page.content().get(0).messageCount()).isEqualTo(2);
        assertThat(page.content().get(0).firstQuestion()).isEqualTo("최근 질문");
        assertThat(page.content().get(0).startedAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 9, 0));
        assertThat(page.content().get(1).status()).isEqualTo(ChatSessionStatus.COMPLETED);
        assertThat(page.content().get(1).messageCount()).isEqualTo(4);
        assertThat(page.content().get(1).firstQuestion()).isEqualTo("오래된 질문");
    }

    @Test
    @DisplayName("메시지가 없는 상담은 목록과 전체 개수에서 빠진다")
    void excludesSessionsWithoutMessages() {
        endedSession(MEMBER_ID, ChatSessionStatus.EXPIRED, LocalDateTime.of(2026, 10, 1, 9, 0));
        ChatSession withMessage = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 2, 9, 0));
        addMessage(withMessage, 1, ChatMessageRole.USER, "질문");
        session(MEMBER_ID, LocalDateTime.of(2026, 10, 3, 9, 0));

        PageResponse<ChatSessionSummaryResponse> page =
                list(1, 10, null);

        assertThat(page.content()).extracting(ChatSessionSummaryResponse::sessionId)
                .containsExactly(withMessage.getId());
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 회원의 상담은 보이지 않는다")
    void excludesOtherMembersSessions() {
        ChatSession others = session(OTHER_MEMBER_ID, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(others, 1, ChatMessageRole.USER, "남의 질문");

        PageResponse<ChatSessionSummaryResponse> page =
                list(1, 10, null);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("첫 질문은 앞 50자(코드 포인트 기준)로 자른다")
    void truncatesFirstQuestionTo50Chars() {
        String longQuestion = "가".repeat(49) + "😀" + "나머지";
        ChatSession session = session(MEMBER_ID, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(session, 1, ChatMessageRole.USER, longQuestion);

        String firstQuestion = list(1, 10, null)
                .content().get(0).firstQuestion();

        assertThat(firstQuestion).isEqualTo("가".repeat(49) + "😀");
        assertThat(firstQuestion.codePointCount(0, firstQuestion.length())).isEqualTo(50);
    }

    @Test
    @DisplayName("페이지는 1부터이고, 요청한 크기만큼 나눠 준다")
    void paginates() {
        for (int day = 1; day <= 3; day++) {
            ChatSession session = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, day, 9, 0));
            addMessage(session, 1, ChatMessageRole.USER, "질문 " + day);
        }

        PageResponse<ChatSessionSummaryResponse> first =
                list(1, 2, null);
        PageResponse<ChatSessionSummaryResponse> second =
                list(2, 2, null);

        assertThat(first.content()).extracting(ChatSessionSummaryResponse::firstQuestion)
                .containsExactly("질문 3", "질문 2");
        assertThat(first.pageNumber()).isEqualTo(1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.isLast()).isFalse();
        assertThat(second.content()).extracting(ChatSessionSummaryResponse::firstQuestion)
                .containsExactly("질문 1");
        assertThat(second.pageNumber()).isEqualTo(2);
        assertThat(second.isLast()).isTrue();
    }

    @Test
    @DisplayName("종료·만료된 본인 상담도 기존 메시지 조회 API로 대화를 볼 수 있다")
    void returnsMessages_whenSessionIsEnded() {
        ChatSession completed = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(completed, 1, ChatMessageRole.USER, "완료 질문");
        ChatSession expired = endedSession(MEMBER_ID, ChatSessionStatus.EXPIRED, LocalDateTime.of(2026, 10, 2, 9, 0));
        addMessage(expired, 1, ChatMessageRole.USER, "만료 질문");

        assertThat(chatMessageService.getMessages(MEMBER_ID, completed.getId()))
                .extracting(ChatMessageResponse::content).containsExactly("완료 질문");
        assertThat(chatMessageService.getMessages(MEMBER_ID, expired.getId()))
                .extracting(ChatMessageResponse::content).containsExactly("만료 질문");
    }

    @Test
    @DisplayName("마지막 메시지(역할 무관) 앞 50자를 미리보기로 준다")
    void givesLastMessagePreview() {
        ChatSession session = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(session, 1, ChatMessageRole.USER, "감자로 뭐 만들까요?");
        addMessage(session, 2, ChatMessageRole.ASSISTANT, "다".repeat(49) + "😀" + "뒷부분");
        ChatSession userLast = session(MEMBER_ID, LocalDateTime.of(2026, 10, 2, 9, 0));
        addMessage(userLast, 1, ChatMessageRole.USER, "첫 질문");
        addMessage(userLast, 2, ChatMessageRole.ASSISTANT, "답변");
        addMessage(userLast, 3, ChatMessageRole.USER, "마지막 질문");

        List<ChatSessionSummaryResponse> content = list(1, 10, null).content();

        assertThat(content).extracting(ChatSessionSummaryResponse::lastMessagePreview)
                .containsExactly("마지막 질문", "다".repeat(49) + "😀");
    }

    @Test
    @DisplayName("상태로 거르면 ACTIVE는 진행 중인 상담만, ENDED는 종료·만료된 상담만 나온다")
    void filtersByStatus() {
        ChatSession completed = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(completed, 1, ChatMessageRole.USER, "종료");
        ChatSession expired = endedSession(MEMBER_ID, ChatSessionStatus.EXPIRED, LocalDateTime.of(2026, 10, 2, 9, 0));
        addMessage(expired, 1, ChatMessageRole.USER, "만료");
        ChatSession others = session(OTHER_MEMBER_ID, LocalDateTime.of(2026, 10, 3, 9, 0));
        addMessage(others, 1, ChatMessageRole.USER, "다른 회원 진행 중");
        ChatSession active = session(MEMBER_ID, LocalDateTime.of(2026, 10, 4, 9, 0));
        addMessage(active, 1, ChatMessageRole.USER, "진행 중");

        assertThat(list(1, 10, ChatSessionListStatus.ALL).content())
                .extracting(ChatSessionSummaryResponse::sessionId)
                .containsExactly(active.getId(), expired.getId(), completed.getId());
        PageResponse<ChatSessionSummaryResponse> activeTab = list(1, 10, ChatSessionListStatus.ACTIVE);
        assertThat(activeTab.content()).extracting(ChatSessionSummaryResponse::sessionId)
                .containsExactly(active.getId());
        assertThat(activeTab.totalElements()).isEqualTo(1);
        PageResponse<ChatSessionSummaryResponse> ended = list(1, 10, ChatSessionListStatus.ENDED);
        assertThat(ended.content()).extracting(ChatSessionSummaryResponse::sessionId)
                .containsExactly(expired.getId(), completed.getId());
        assertThat(ended.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("시간 초과로 아직 ACTIVE인 상담은 만료로 보여 주고, ENDED 필터와 종료 건수에 들어간다")
    void treatsTimedOutActiveAsExpired() {
        ChatSession timedOut = session(MEMBER_ID, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(timedOut, 1, ChatMessageRole.USER, "시간 초과");
        jdbcTemplate.update("UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(2), timedOut.getId());

        ChatSessionListResponse all = chatSessionService.getMySessions(MEMBER_ID, new ChatSessionListRequest(null, null, null));

        assertThat(all.sessions().content().get(0).status()).isEqualTo(ChatSessionStatus.EXPIRED);
        assertThat(all.counts()).isEqualTo(new ChatSessionStatusCounts(1, 0, 1));
        assertThat(list(1, 10, ChatSessionListStatus.ACTIVE).content()).isEmpty();
        assertThat(list(1, 10, ChatSessionListStatus.ENDED).content())
                .extracting(ChatSessionSummaryResponse::sessionId).containsExactly(timedOut.getId());
    }

    @Test
    @DisplayName("탭 건수는 상태 필터와 상관없이 전체·진행 중·종료(종료+만료)를 주고, 메시지 없는 상담은 세지 않는다")
    void countsByStatus() {
        ChatSession completed = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 1, 9, 0));
        addMessage(completed, 1, ChatMessageRole.USER, "종료");
        ChatSession expired = endedSession(MEMBER_ID, ChatSessionStatus.EXPIRED, LocalDateTime.of(2026, 10, 2, 9, 0));
        addMessage(expired, 1, ChatMessageRole.USER, "만료");
        endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, 3, 9, 0));
        ChatSession active = session(MEMBER_ID, LocalDateTime.of(2026, 10, 4, 9, 0));
        addMessage(active, 1, ChatMessageRole.USER, "진행 중");

        ChatSessionListResponse activeTab = chatSessionService.getMySessions(MEMBER_ID,
                new ChatSessionListRequest(1, 10, ChatSessionListStatus.ACTIVE));

        assertThat(activeTab.counts()).isEqualTo(new ChatSessionStatusCounts(3, 1, 2));
        assertThat(chatSessionService.getMySessions(OTHER_MEMBER_ID, new ChatSessionListRequest(null, null, null))
                .counts()).isEqualTo(new ChatSessionStatusCounts(0, 0, 0));
    }

    @Test
    @DisplayName("필터를 건 목록도 다음 페이지가 있는지(isLast)를 알려 준다")
    void tellsWhetherNextPageExists_whenFiltered() {
        for (int day = 1; day <= 3; day++) {
            ChatSession session = endedSession(MEMBER_ID, ChatSessionStatus.COMPLETED, LocalDateTime.of(2026, 10, day, 9, 0));
            addMessage(session, 1, ChatMessageRole.USER, "질문 " + day);
        }
        ChatSession active = session(MEMBER_ID, LocalDateTime.of(2026, 10, 4, 9, 0));
        addMessage(active, 1, ChatMessageRole.USER, "진행 중");

        PageResponse<ChatSessionSummaryResponse> first = list(1, 2, ChatSessionListStatus.ENDED);
        PageResponse<ChatSessionSummaryResponse> second = list(2, 2, ChatSessionListStatus.ENDED);

        assertThat(first.isLast()).isFalse();
        assertThat(second.isLast()).isTrue();
        assertThat(second.content()).extracting(ChatSessionSummaryResponse::firstQuestion).containsExactly("질문 1");
        assertThat(list(1, 2, ChatSessionListStatus.ACTIVE).isLast()).isTrue();
    }

    private PageResponse<ChatSessionSummaryResponse> list(Integer page, Integer size, ChatSessionListStatus status) {
        return chatSessionService.getMySessions(MEMBER_ID, new ChatSessionListRequest(page, size, status)).sessions();
    }

    private ChatSession session(Long memberId, LocalDateTime startedAt) {
        ChatSession session = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(memberId).build());
        jdbcTemplate.update("UPDATE chat_sessions SET created_at = ? WHERE id = ?", startedAt, session.getId());
        return chatSessionRepository.findById(session.getId()).orElseThrow();
    }

    private ChatSession endedSession(Long memberId, ChatSessionStatus status, LocalDateTime startedAt) {
        ChatSession session = session(memberId, startedAt);
        jdbcTemplate.update("UPDATE chat_sessions SET status = ? WHERE id = ?", status.name(), session.getId());
        return chatSessionRepository.findById(session.getId()).orElseThrow();
    }

    private void addMessage(ChatSession session, int sequence, ChatMessageRole role, String content) {
        chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .session(session)
                .sequence(sequence)
                .role(role)
                .content(content)
                .build());
    }
}
