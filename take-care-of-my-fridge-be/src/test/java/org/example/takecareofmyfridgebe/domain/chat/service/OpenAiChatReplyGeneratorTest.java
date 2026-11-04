package org.example.takecareofmyfridgebe.domain.chat.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiChatReplyGeneratorTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);

    private final OpenAiChatReplyGenerator generator = new OpenAiChatReplyGenerator(
            chatClient,
            new LlmCostCalculator(0.15, 0.60),
            new ChatReplyErrorTranslator(),
            new ByteArrayResource("공통".getBytes(StandardCharsets.UTF_8)),
            new ByteArrayResource("추천".getBytes(StandardCharsets.UTF_8))
    );

    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);

        logger = (Logger) LoggerFactory.getLogger(OpenAiChatReplyGenerator.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("응답 조각에서 글자만 꺼내 순서대로 보내고 빈 글자와 사용량만 있는 조각은 거른다")
    void emitsTextOnlyAndSkipsEmptyChunks() {
        when(streamSpec.chatResponse()).thenReturn(Flux.just(
                textChunk(""),
                textChunk("된장"),
                textChunk("찌개"),
                finishChunk("STOP"),
                usageChunk(100, 50)
        ));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "된장찌개 알려줘"))
                .expectNext("된장", "찌개")
                .verifyComplete();
    }

    @Test
    @DisplayName("마지막 사용량 조각을 받으면 회원·상담·모델·토큰·비용을 INFO 로그로 남긴다")
    void logsUsageAndCost_whenUsageChunkArrives() {
        when(streamSpec.chatResponse()).thenReturn(Flux.just(textChunk("답"), finishChunk("STOP"), usageChunk(1000, 500)));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "질문"))
                .expectNext("답")
                .verifyComplete();

        List<ILoggingEvent> usageLogs = logs.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .toList();
        assertThat(usageLogs).hasSize(1);
        assertThat(usageLogs.get(0).getFormattedMessage())
                .contains("memberId: 1", "sessionId: 10", "모델: gpt-4o-mini", "입력: 1000", "출력: 500", "비용: $0.000450");
    }

    @Test
    @DisplayName("조각마다 0 사용량이 오고 마지막 조각에만 실제 사용량이 오면 스트림이 끝날 때 실제 사용량으로 한 번만 로그를 남긴다")
    void logsOnceWithRealUsage_whenOtherChunksHaveZeroUsage() {
        when(streamSpec.chatResponse()).thenReturn(Flux.just(
                zeroUsageChunk("계란"),
                zeroUsageChunk("찜은"),
                zeroUsageChunk(" 쉬워요"),
                finishChunk("STOP"),
                usageChunk(163, 37)
        ));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "계란찜"))
                .expectNext("계란", "찜은", " 쉬워요")
                .verifyComplete();

        assertThat(logs.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .singleElement()
                .asString()
                .contains("입력: 163", "출력: 37", "모델: gpt-4o-mini");
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN).isEmpty();
    }

    @Test
    @DisplayName("끝까지 0보다 큰 사용량을 받지 못하면 사용량 로그 대신 WARN 한 줄을 남긴다")
    void logsWarn_whenUsageNeverArrives() {
        when(streamSpec.chatResponse()).thenReturn(Flux.just(zeroUsageChunk("답"), finishChunk("STOP")));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "질문"))
                .expectNext("답")
                .verifyComplete();

        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.INFO).isEmpty();
        assertThat(logs.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .singleElement()
                .asString()
                .contains("사용량을 받지 못함", "memberId: 1", "sessionId: 10");
    }

    @Test
    @DisplayName("finishReason이 length면 WARN 로그를 남기고 받은 답은 그대로 보낸다")
    void logsWarnAndKeepsReply_whenFinishReasonIsLength() {
        when(streamSpec.chatResponse()).thenReturn(Flux.just(
                textChunk("잘린"), textChunk(" 답"), finishChunk("LENGTH"), usageChunk(100, 800)));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "질문"))
                .expectNext("잘린", " 답")
                .verifyComplete();

        assertThat(logs.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .singleElement()
                .asString()
                .contains("최대 토큰", "sessionId: 10");
    }

    @Test
    @DisplayName("첫 조각 전에 OpenAI 요청 한도 오류가 나면 CHAT_AI_BUSY로 바꿔 보낸다")
    void throwsAiBusy_whenRateLimitedBeforeFirstChunk() {
        when(streamSpec.chatResponse()).thenReturn(Flux.error(
                new IllegalStateException("Stream processing failed", rateLimit())));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "질문"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_BUSY))
                .verify();
    }

    @Test
    @DisplayName("첫 조각을 보낸 뒤 OpenAI 오류가 나면 CHAT_REPLY_INTERRUPTED로 바꿔 보낸다")
    void throwsInterrupted_whenFailsAfterFirstChunk() {
        when(streamSpec.chatResponse()).thenReturn(Flux.concat(
                Flux.just(textChunk("")),
                Flux.just(textChunk("조각")),
                Flux.error(rateLimit())));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "질문"))
                .expectNext("조각")
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.CHAT_REPLY_INTERRUPTED))
                .verify();
    }

    @Test
    @DisplayName("빈 글자 조각만 받은 뒤 오류가 나면 첫 조각 전 실패로 본다")
    void treatsAsBeforeFirstChunk_whenOnlyEmptyChunksArrived() {
        when(streamSpec.chatResponse()).thenReturn(Flux.concat(Flux.just(textChunk("")), Flux.error(rateLimit())));

        StepVerifier.create(generator.generate(MEMBER_ID, SESSION_ID, "질문"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_BUSY))
                .verify();
    }

    private ChatResponse textChunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadataWithoutUsage());
    }

    // 실제 OpenAI 스트림은 사용량이 없는 조각에도 0으로 채운 사용량을 담아 온다
    private ChatResponse zeroUsageChunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), ChatResponseMetadata.builder()
                .model("gpt-4o-mini")
                .usage(new DefaultUsage(0, 0))
                .build());
    }

    private ChatResponse finishChunk(String finishReason) {
        Generation generation = new Generation(
                new AssistantMessage(""),
                ChatGenerationMetadata.builder().finishReason(finishReason).build()
        );
        return new ChatResponse(List.of(generation), metadataWithoutUsage());
    }

    // OpenAI는 include_usage일 때 choices가 빈 마지막 조각에 사용량을 담아 보낸다
    private ChatResponse usageChunk(int inputTokens, int outputTokens) {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .model("gpt-4o-mini")
                .usage(new DefaultUsage(inputTokens, outputTokens))
                .build());
    }

    private ChatResponseMetadata metadataWithoutUsage() {
        return ChatResponseMetadata.builder().model("gpt-4o-mini").build();
    }

    private RateLimitException rateLimit() {
        return RateLimitException.builder()
                .headers(Headers.builder().build())
                .error(ErrorObject.builder()
                        .code("rate_limit_exceeded")
                        .message("테스트 오류")
                        .param(Optional.empty())
                        .type("requests")
                        .build())
                .build();
    }
}
