package org.example.takecareofmyfridgebe.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationToSave;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSession;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatSessionStatus;
import org.example.takecareofmyfridgebe.domain.chat.push.ChatSessionPushPublisher;
import org.example.takecareofmyfridgebe.domain.chat.repository.ChatSessionRepository;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatSessionService;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.cooking.event.CookingCompletedEvent;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 요리 완료 트랜잭션(TransactionTemplate으로 흉내)이 커밋된 뒤에만 상담이 끝나고 신호가 나가는지,
// 상담 종료가 실패해도 요리 기록은 남는지 실제 MySQL로 본다. 발행기는 mock으로 바꿔 신호만 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ChatProperties.class)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ChatSessionService.class, ChatMessageService.class,
        ChatSessionChangedEventListener.class, CookingCompletedEventListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CookingCompletedEventTransactionTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 901L;
    private static final Long RECIPE_ID = 12L;

    @MockitoBean
    private ChatSessionPushPublisher chatSessionPushPublisher;

    @MockitoSpyBean
    private ChatSessionService chatSessionService;

    private final ChatMessageService chatMessageService;
    private final ChatSessionRepository chatSessionRepository;
    private final CookingLogRepository cookingLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CookingCompletedEventTransactionTest(
            ChatMessageService chatMessageService,
            ChatSessionRepository chatSessionRepository,
            CookingLogRepository cookingLogRepository,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            DataSource dataSource) {
        this.chatMessageService = chatMessageService;
        this.chatSessionRepository = chatSessionRepository;
        this.cookingLogRepository = cookingLogRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM cooking_logs");
        jdbcTemplate.execute("DELETE FROM chat_recommendations");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_sessions");
    }

    @Test
    @DisplayName("진행 중 상담에서 추천한 레시피는 그 상담을 찾고, 추천하지 않은 레시피·30분 지난 상담은 찾지 않는다")
    void findsSessionOnlyForRecommendedRecipe() {
        Long sessionId = saveActiveSession();
        chatMessageService.saveExchange(sessionId, "두부 요리 추천해줘", "두부조림 어때요?",
                List.of(new RecommendationToSave(RECIPE_ID, "두부조림"), RecommendationToSave.mealkit(77L, "밀키트")));

        assertThat(chatSessionService.findCookingSessionId(MEMBER_ID, null, RECIPE_ID)).contains(sessionId);
        assertThat(chatSessionService.findCookingSessionId(MEMBER_ID, null, 13L)).isEmpty();
        // 밀키트 카드의 상품 ID는 레시피 추천으로 보지 않는다
        assertThat(chatSessionService.findCookingSessionId(MEMBER_ID, null, 77L)).isEmpty();

        makeIdle(sessionId);
        assertThat(chatSessionService.findCookingSessionId(MEMBER_ID, null, RECIPE_ID)).isEmpty();
    }

    @Test
    @DisplayName("요리 완료가 커밋되면 그 뒤에 상담을 COMPLETED로 끝내고 신호를 한 번 발행한다")
    void completesSessionAfterCommit() {
        Long sessionId = saveActiveSession();

        transactionTemplate.executeWithoutResult(status -> {
            Long cookingLogId = saveCookingLog(sessionId, CookingResult.SUCCESS);
            eventPublisher.publishEvent(new CookingCompletedEvent(MEMBER_ID, sessionId, cookingLogId));
            assertThat(statusOf(sessionId)).isEqualTo(ChatSessionStatus.ACTIVE);
            verifyNoInteractions(chatSessionPushPublisher);
        });

        assertThat(statusOf(sessionId)).isEqualTo(ChatSessionStatus.COMPLETED);
        verify(chatSessionPushPublisher).publish(MEMBER_ID, sessionId, ChatSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("요리 결과가 실패여도 커밋되면 상담을 끝낸다")
    void completesSession_whenResultIsFailed() {
        Long sessionId = saveActiveSession();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                new CookingCompletedEvent(MEMBER_ID, sessionId, saveCookingLog(sessionId, CookingResult.FAILED))));

        assertThat(statusOf(sessionId)).isEqualTo(ChatSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("요리 완료가 롤백되면 상담은 그대로이고 신호도 발행하지 않는다")
    void keepsSession_whenCookingRollsBack() {
        Long sessionId = saveActiveSession();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new CookingCompletedEvent(MEMBER_ID, sessionId, saveCookingLog(sessionId, CookingResult.SUCCESS)));
            status.setRollbackOnly();
        });

        assertThat(statusOf(sessionId)).isEqualTo(ChatSessionStatus.ACTIVE);
        assertThat(cookingLogRepository.count()).isZero();
        verifyNoInteractions(chatSessionPushPublisher);
    }

    @Test
    @DisplayName("상담 종료가 실패해도 요리 완료는 예외 없이 끝나고 요리 기록은 남는다")
    void keepsCookingLog_whenCompletingSessionFails() {
        Long sessionId = saveActiveSession();
        doThrow(new IllegalStateException("상담 저장 실패"))
                .when(chatSessionService).completeSessionFromCooking(MEMBER_ID, sessionId);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                new CookingCompletedEvent(MEMBER_ID, sessionId, saveCookingLog(sessionId, CookingResult.SUCCESS))));

        assertThat(cookingLogRepository.findAll()).extracting(CookingLog::getChatSessionId).containsExactly(sessionId);
        assertThat(statusOf(sessionId)).isEqualTo(ChatSessionStatus.ACTIVE);
        verify(chatSessionPushPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("요리 완료 중에 상담이 만료되면 요리 기록은 남고 상담은 만료 그대로 둔다")
    void keepsExpired_whenSessionExpiresDuringCooking() {
        Long sessionId = saveActiveSession();

        transactionTemplate.executeWithoutResult(status -> {
            Long cookingLogId = saveCookingLog(sessionId, CookingResult.SUCCESS);
            // 다른 요청(새 상담 시작)이 먼저 만료시켰다
            chatSessionService.expireSession(sessionId);
            eventPublisher.publishEvent(new CookingCompletedEvent(MEMBER_ID, sessionId, cookingLogId));
        });

        assertThat(cookingLogRepository.count()).isEqualTo(1);
        assertThat(statusOf(sessionId)).isEqualTo(ChatSessionStatus.EXPIRED);
        verify(chatSessionPushPublisher, never()).publish(MEMBER_ID, sessionId, ChatSessionStatus.COMPLETED);
    }

    private Long saveActiveSession() {
        return chatSessionRepository.saveAndFlush(ChatSession.builder().memberId(MEMBER_ID).build()).getId();
    }

    private Long saveCookingLog(Long sessionId, CookingResult result) {
        return cookingLogRepository.save(CookingLog.builder()
                .memberId(MEMBER_ID)
                .recipeId(RECIPE_ID)
                .chatSessionId(sessionId)
                .result(result)
                .build()).getId();
    }

    private ChatSessionStatus statusOf(Long sessionId) {
        return ChatSessionStatus.valueOf(
                jdbcTemplate.queryForObject("SELECT status FROM chat_sessions WHERE id = ?", String.class, sessionId));
    }

    private void makeIdle(Long sessionId) {
        jdbcTemplate.update("UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(35), sessionId);
    }
}
