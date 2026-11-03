package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCost;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// OpenAI(Spring AI) 스트리밍으로 상담 답을 만든다. app.chat.reply-generator=openai일 때만 빈이 된다
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "openai")
public class OpenAiChatReplyGenerator implements ChatReplyGenerator {

    private static final String FINISH_REASON_LENGTH = "length";

    private final ChatClient chatClient;
    private final LlmCostCalculator llmCostCalculator;
    private final ChatReplyErrorTranslator chatReplyErrorTranslator;

    public OpenAiChatReplyGenerator(
            @Qualifier("chatReplyChatClient") ChatClient chatClient,
            LlmCostCalculator llmCostCalculator,
            ChatReplyErrorTranslator chatReplyErrorTranslator
    ) {
        this.chatClient = chatClient;
        this.llmCostCalculator = llmCostCalculator;
        this.chatReplyErrorTranslator = chatReplyErrorTranslator;
    }

    /**
     * 응답 조각에서 글자만 꺼내 보낸다(빈 문자열은 거른다).
     * 사용량은 OpenAI가 마지막에 따로 보내는 조각에만 있고, 나머지 조각은 0으로 온다.
     * 그래서 0보다 큰 사용량만 기억해 두었다가 스트림이 끝날 때 한 번만 비용 로그를 남긴다.
     * 오류는 첫 조각을 보냈는지에 따라 상담 에러 코드로 바꾼다.
     */
    @Override
    public Flux<String> generate(Long memberId, Long sessionId, String userMessage) {
        return Flux.defer(() -> {
            AtomicBoolean firstChunkSent = new AtomicBoolean(false);
            AtomicReference<ReplyUsage> lastUsage = new AtomicReference<>();
            return chatClient.prompt()
                    .user(userMessage)
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        rememberUsage(response, lastUsage);
                        warnIfTruncated(memberId, sessionId, response);
                    })
                    .map(this::textOf)
                    .filter(text -> !text.isEmpty())
                    .doOnNext(text -> firstChunkSent.set(true))
                    .doOnComplete(() -> logUsage(memberId, sessionId, lastUsage.get()))
                    .onErrorMap(error -> chatReplyErrorTranslator.translate(error, firstChunkSent.get()));
        });
    }

    private String textOf(ChatResponse response) {
        Generation result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return "";
        }
        String text = result.getOutput().getText();
        return text == null ? "" : text;
    }

    // 입력+출력 토큰이 0보다 큰 사용량만 기억한다 (사용량이 없는 조각은 0 또는 EmptyUsage로 온다)
    private void rememberUsage(ChatResponse response, AtomicReference<ReplyUsage> lastUsage) {
        llmCostCalculator.calculate(response)
                .filter(cost -> cost.inputTokens() + cost.outputTokens() > 0)
                .ifPresent(cost -> lastUsage.set(new ReplyUsage(response.getMetadata().getModel(), cost)));
    }

    private void logUsage(Long memberId, Long sessionId, ReplyUsage usage) {
        if (usage == null) {
            log.warn("상담 답 LLM 사용량을 받지 못함 - memberId: {}, sessionId: {}", memberId, sessionId);
            return;
        }
        LlmCost cost = usage.cost();
        log.info("상담 답 LLM 사용량 - memberId: {}, sessionId: {}, 모델: {}, 입력: {}, 출력: {}, 비용: ${}",
                memberId, sessionId, usage.model(), cost.inputTokens(), cost.outputTokens(),
                String.format("%.6f", cost.costUsd()));
    }

    private void warnIfTruncated(Long memberId, Long sessionId, ChatResponse response) {
        Generation result = response.getResult();
        if (result != null && result.getMetadata() != null
                && FINISH_REASON_LENGTH.equalsIgnoreCase(result.getMetadata().getFinishReason())) {
            log.warn("상담 답이 최대 토큰에 걸려 잘림 - memberId: {}, sessionId: {}, 모델: {}",
                    memberId, sessionId, response.getMetadata().getModel());
        }
    }

    private record ReplyUsage(String model, LlmCost cost) {
    }
}
