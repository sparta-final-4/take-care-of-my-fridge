package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationToSave;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatMessageRepository;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
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
import java.util.Map;

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

        jdbcTemplate.execute("DELETE FROM chat_recommendations");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    // 같은 MySQL을 쓰는 다른 테스트 클래스가 상담·메시지를 지울 수 있게 추천까지 지워 둔다
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM chat_recommendations");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("saveExchange: 보내면 두 메시지가 sequence 1, 2로 저장되고, 한 번 더 보내면 3, 4로 저장된다")
    void savesExchangeWithIncreasingSequence() {
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
    void throwsConflictWithoutSaving_whenSessionCompletesBeforeSave() {
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

    @Test
    @DisplayName("saveExchange: 추천 카드 3개를 AI 메시지에 보여 준 순서대로 설명과 함께 저장하고 한 번에 조회한다")
    void savesRecommendationsInOrderWithDescription() {
        ChatSession session = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(101L).build());

        List<ChatMessageResponse> saved = chatMessageService.saveExchange(session.getId(), "뭐 먹지?", "소개", List.of(
                new RecommendationToSave(30L, "첫 카드 설명"),
                new RecommendationToSave(10L, null),
                new RecommendationToSave(20L, "셋째 카드 설명")));

        Long assistantId = saved.get(1).id();
        assertThat(chatMessageService.findRecipeRecommendations(List.of(assistantId))).containsExactly(
                new SavedRecommendation(assistantId, 30L, 0, "첫 카드 설명"),
                new SavedRecommendation(assistantId, 10L, 1, null),
                new SavedRecommendation(assistantId, 20L, 2, "셋째 카드 설명"));
    }

    @Test
    @DisplayName("saveExchange: 추천 카드가 없으면 추천을 저장하지 않는다")
    void savesNoRecommendations_whenNoCards() {
        ChatSession session = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(102L).build());

        List<ChatMessageResponse> saved = chatMessageService.saveExchange(session.getId(), "두부 보관?", "냉장 보관");

        assertThat(chatMessageService.findRecipeRecommendations(List.of(saved.get(1).id()))).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_recommendations", Long.class)).isZero();
    }

    @Test
    @DisplayName("이 상담에서 추천한 레시피 ID를 중복 없이 읽고, 다른 상담의 추천은 넣지 않는다")
    void findsRecommendedRecipeIdsOfSession() {
        ChatSession session = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(103L).build());
        ChatSession other = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(104L).build());
        chatMessageService.saveExchange(session.getId(), "뭐 먹지?", "소개",
                List.of(new RecommendationToSave(10L, null), new RecommendationToSave(20L, null)));
        chatMessageService.saveExchange(session.getId(), "또 추천", "소개",
                List.of(new RecommendationToSave(20L, null), new RecommendationToSave(30L, null)));
        chatMessageService.saveExchange(other.getId(), "다른 상담", "소개", List.of(new RecommendationToSave(99L, null)));

        assertThat(chatMessageService.findRecommendedRecipeIds(session.getId())).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    @DisplayName("상담마다 최근 답부터 카드 순서로 추천 레시피를 중복 없이 최대 3개 읽는다")
    void findsRecentRecommendedRecipeIdsPerSession() {
        ChatSession session = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(105L).build());
        ChatSession other = chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(106L).build());
        chatMessageService.saveExchange(session.getId(), "첫 추천", "소개",
                List.of(new RecommendationToSave(10L, null), new RecommendationToSave(20L, null)));
        chatMessageService.saveExchange(session.getId(), "두 번째 추천", "소개",
                List.of(new RecommendationToSave(30L, null), new RecommendationToSave(10L, null), new RecommendationToSave(40L, null)));
        chatMessageService.saveExchange(other.getId(), "다른 상담", "소개", List.of(new RecommendationToSave(99L, null)));

        Map<Long, List<Long>> result = chatMessageService.findRecentRecommendedRecipeIds(
                List.of(session.getId(), other.getId()), 3);

        assertThat(result.get(session.getId())).containsExactly(30L, 10L, 40L);
        assertThat(result.get(other.getId())).containsExactly(99L);
    }
}
