package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatHealthWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageCreateRequest;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatMessageResponse;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatContextFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatFridgeShopFacade;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatRecommendationFacade;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

// 실제 Redis에 잠금·하루 횟수를 두고, 같은 상담에 두 창이 동시에 보내는 상황을 흉내 낸다.
// 답 생성은 끝나지 않는 Sink로 붙잡아 두어 먼저 잡은 요청이 답을 받는 중인 상태를 만든다
@SpringBootTest(classes = {
        RedisConfig.class,
        ChatStreamService.class,
        ChatStreamLockService.class,
        ChatDailyLimitService.class,
        ChatStreamConcurrencyTest.TestConfig.class
})
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@EnableConfigurationProperties({ChatProperties.class, ChatSafetyWordProperties.class, ChatHealthWordProperties.class})
class ChatStreamConcurrencyTest extends RedisContainerSupport {

    private static final Long MEMBER_ID = 801L;
    private static final Long SESSION_ID = 901L;

    @Autowired
    private ChatStreamService chatStreamService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ChatMessageService chatMessageService;

    @MockitoBean
    private ChatReplyGenerator chatReplyGenerator;

    @MockitoBean
    private ChatIntentFacade chatIntentFacade;

    @MockitoBean
    private ChatRecommendationFacade chatRecommendationFacade;

    @MockitoBean
    private ChatFridgeShopFacade chatFridgeShopFacade;

    @MockitoBean
    private ChatContextFacade chatContextFacade;

    @MockitoBean
    private ChatContextStore chatContextStore;

    @BeforeEach
    void clearKeys() {
        given(chatContextFacade.load(any())).willReturn(ChatContext.empty());
        for (String pattern : List.of("chat:streaming:*", "chat:limit:*")) {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    @Test
    @DisplayName("같은 상담에 동시에 두 번 보내면 하나만 스트림을 받고 다른 하나는 409, 하루 횟수는 1만 오른다")
    void streamsOnlyOne_whenSentConcurrently() throws Exception {
        Sinks.Many<String> reply = Sinks.many().unicast().onBackpressureBuffer();
        given(chatReplyGenerator.generate(anyLong(), anyLong(), any(), any())).willReturn(reply.asFlux());

        int requests = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Flux<ServerSentEvent<String>>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                Callable<Flux<ServerSentEvent<String>>> send = () -> {
                    ready.countDown();
                    start.await();
                    return chatStreamService.streamMessage(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("질문"));
                };
                futures.add(executor.submit(send));
            }
            ready.await();
            start.countDown();

            List<Flux<ServerSentEvent<String>>> streams = new ArrayList<>();
            List<ErrorCode> failures = new ArrayList<>();
            for (Future<Flux<ServerSentEvent<String>>> future : futures) {
                try {
                    streams.add(future.get(5, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof BusinessException be) {
                        failures.add(be.getErrorCode());
                    } else {
                        throw e;
                    }
                }
            }

            assertThat(streams).hasSize(1);
            assertThat(failures).containsExactly(ErrorCode.CHAT_REPLY_IN_PROGRESS);
            assertThat(todayCount()).isEqualTo("1");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("답을 다 받거나(완료), 생성이 실패하거나(오류), 연결이 끊긴(취소) 뒤에는 같은 상담에 다시 보낼 수 있다")
    void allowsResend_whenPreviousStreamEnds() {
        given(chatMessageService.saveExchange(anyLong(), any(), any())).willReturn(List.of(
                new ChatMessageResponse(1L, 1, ChatMessageRole.USER, "질문", LocalDateTime.now()),
                new ChatMessageResponse(2L, 2, ChatMessageRole.ASSISTANT, "답", LocalDateTime.now())
        ));
        given(chatReplyGenerator.generate(anyLong(), anyLong(), any(), any())).willReturn(
                Flux.just("답"),
                Flux.error(new RuntimeException("AI 연결 실패")),
                Flux.concat(Flux.just("조각"), Flux.never()),
                Flux.just("답")
        );

        // 완료
        StepVerifier.create(chatStreamService.streamMessage(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("질문")))
                .expectNextMatches(event -> "message".equals(event.event()))
                .expectNextMatches(event -> "done".equals(event.event()))
                .verifyComplete();
        awaitUnlocked();

        // 오류
        StepVerifier.create(chatStreamService.streamMessage(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("질문")))
                .expectNextMatches(event -> "error".equals(event.event()))
                .verifyComplete();
        awaitUnlocked();

        // 취소 (연결 끊김)
        StepVerifier.create(chatStreamService.streamMessage(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("질문")))
                .expectNextMatches(event -> "message".equals(event.event()))
                .thenCancel()
                .verify();
        awaitUnlocked();

        StepVerifier.create(chatStreamService.streamMessage(MEMBER_ID, SESSION_ID, ChatMessageCreateRequest.of("질문")))
                .expectNextMatches(event -> "message".equals(event.event()))
                .expectNextMatches(event -> "done".equals(event.event()))
                .verifyComplete();

        // 4번 보냈지만 오류는 첫 조각 전 실패라 하루 횟수를 돌려받아 3이다
        assertThat(todayCount()).isEqualTo("3");
    }

    // doFinally는 마지막 신호를 받는 쪽에 넘긴 직후 실행되므로, 다음 요청 전에 잠금이 풀리길 잠깐 기다린다
    private void awaitUnlocked() {
        long deadline = System.currentTimeMillis() + 2_000;
        while (Boolean.TRUE.equals(stringRedisTemplate.hasKey("chat:streaming:" + SESSION_ID))) {
            assertThat(System.currentTimeMillis()).as("잠금이 2초 안에 풀려야 한다").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private String todayCount() {
        String today = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return stringRedisTemplate.opsForValue().get("chat:limit:" + MEMBER_ID + ":" + today);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
