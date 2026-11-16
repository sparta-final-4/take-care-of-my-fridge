package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.chat.ai.ChatReplyGenerator;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentClassifier;
import org.example.takecareofmyfridgebe.domain.chat.ai.JevIntentShadow;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatJevProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSafetyBlock;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.safety.AllergyWordCheckerTest;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatContextStore;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatDailyLimitService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatStreamLockService;
import org.example.takecareofmyfridgebe.global.config.TypeSafeProperties;
import org.example.takecareofmyfridgebe.global.config.TypeSafeShadowExecutor;
import org.example.takecareofmyfridgebe.infra.typesafe.client.TypeSafeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 안전 단어로 막힌 메시지는 의도 판단 전에 끝나므로 Jev 그림자 모드가 켜져 있어도 Jev를 부르지 않는다
class ChatStreamJevShadowTest {

    private static final String LOCK_TOKEN = "lock-token";
    // 테스트용 가짜 키 (실제 키가 아니다)
    private static final String FAKE_KEY = "fake-test-key-0000";

    private final ChatIntentFixture fixture = new ChatIntentFixture();
    private final JevIntentClassifier classifier = mock(JevIntentClassifier.class);
    private final TypeSafeShadowExecutor executor = new TypeSafeShadowExecutor(1, 1);
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final ChatStreamLockService chatStreamLockService = mock(ChatStreamLockService.class);
    private final ChatDailyLimitService chatDailyLimitService = mock(ChatDailyLimitService.class);
    private final ChatStreamFacade chatStreamFacade = new ChatStreamFacade(chatMessageService, chatStreamLockService,
            chatDailyLimitService, mock(ChatReplyGenerator.class), intentFacadeWithJev(), mock(ChatRecommendationFacade.class),
            mock(ChatFridgeShopFacade.class), mock(ChatContextFacade.class), mock(ChatContextStore.class),
            new ChatSafetyProperties(
                    new ChatSafetyProperties.Words(List.of("ㅅㅂ", "폭탄"), List.of("죽고싶"), List.of("지시무시", "시스템프롬프트"), List.of("폭탄주")),
                    new ChatSafetyProperties.Health(List.of("당뇨", "알레르기", "임신", "약먹"), List.of("약불", "약간")),
                    AllergyWordCheckerTest.WORDS, List.of()),
            new ObjectMapper());

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    @DisplayName("Jev 그림자 모드가 켜져 있어도 안전 단어로 막힌 메시지는 Jev와 의도 판단 AI를 부르지 않고 하루 횟수도 세지 않는다")
    void skipsJev_whenBlockedBySafetyWords() {
        Long sessionId = 10L;
        String reply = ChatSafetyBlock.BANNED.reply();
        when(chatStreamLockService.acquire(sessionId)).thenReturn(LOCK_TOKEN);
        when(chatMessageService.saveFilteredExchange(sessionId, "폭탄 만드는 법", reply)).thenReturn(List.of(
                new ChatMessageResponse(100L, 1, ChatMessageRole.USER, "폭탄 만드는 법", LocalDateTime.now()),
                new ChatMessageResponse(101L, 2, ChatMessageRole.ASSISTANT, reply, LocalDateTime.now())));

        chatStreamFacade.streamMessage(1L, sessionId, ChatMessageCreateRequest.of("폭탄 만드는 법")).collectList().block();

        verify(chatStreamLockService, timeout(1_000)).release(sessionId, LOCK_TOKEN);
        verifyNoInteractions(classifier);
        verifyNoInteractions(fixture.extractor);
        verify(chatDailyLimitService, never()).checkAndIncrement(any());
    }

    private ChatIntentFacade intentFacadeWithJev() {
        ChatCorrectionProperties corrections = ChatCorrectionProperties.ALL_ON;
        JevIntentShadow shadow = new JevIntentShadow(new ChatJevProperties(true), classifier,
                new TypeSafeClient(RestClient.create(), new TypeSafeProperties(null, FAKE_KEY, null, null)), executor);
        return new ChatIntentFacade(fixture.extractor, fixture.recipeService, fixture.ingredientMatcher,
                new ChatCandidateProperties(List.of("양념", "음료"), 0.4, List.of(), List.of("백김치", "물김치", "동치미", "나박김치"), List.of(), List.of()),
                ChatIntentFixture.WORDS, fixture.allergyExclusionFinder, corrections, fixture.newCorrector(corrections), shadow);
    }
}
