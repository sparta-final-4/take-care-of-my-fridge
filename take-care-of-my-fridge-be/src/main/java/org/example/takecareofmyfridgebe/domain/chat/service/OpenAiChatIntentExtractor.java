package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI(Spring AI)로 상담 메시지의 의도·조건을 JSON으로 뽑는다. app.chat.reply-generator=openai일 때만 빈이 된다.
 * 응답 모양은 IntentResult의 JSON 스키마로 강제한다(ENABLE_NATIVE_STRUCTURED_OUTPUT, OpenAI response_format).
 * <p>
 * 시스템 프롬프트의 분류 목록은 Spring AI 템플릿({이름})이 아니라 문자열 치환(%CATEGORIES%)으로 넣는다.
 * 템플릿 매개변수를 쓰지 않으면 Spring AI는 프롬프트를 그대로 보내므로, 메시지에 {}가 있어도 깨지지 않는다(테스트로 확인).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "openai")
public class OpenAiChatIntentExtractor implements ChatIntentExtractor {

    static final String CATEGORIES_PLACEHOLDER = "%CATEGORIES%";
    private static final String CATEGORY_DELIMITER = ", ";

    private final ChatClient chatClient;
    private final LlmCostCalculator llmCostCalculator;
    private final String promptTemplate;

    public OpenAiChatIntentExtractor(
            @Qualifier("chatIntentChatClient") ChatClient chatClient,
            LlmCostCalculator llmCostCalculator,
            @Value("classpath:prompts/chat-intent.txt") Resource prompt
    ) {
        this.chatClient = chatClient;
        this.llmCostCalculator = llmCostCalculator;
        this.promptTemplate = read(prompt);
    }

    @Override
    public IntentResult extract(Long memberId, Long sessionId, String message, List<String> categories) {
        long startedAt = System.nanoTime();
        ResponseEntity<ChatResponse, IntentResult> response = chatClient.prompt()
                .system(systemPrompt(categories))
                .user(message)
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .responseEntity(IntentResult.class);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        logUsage(memberId, sessionId, response.response(), elapsedMs);
        IntentResult result = response.entity();
        if (result == null) {
            throw new IllegalStateException("상담 의도 응답이 비어 있음");
        }
        return result;
    }

    String systemPrompt(List<String> categories) {
        return promptTemplate.replace(CATEGORIES_PLACEHOLDER, String.join(CATEGORY_DELIMITER, categories));
    }

    private void logUsage(Long memberId, Long sessionId, ChatResponse response, long elapsedMs) {
        llmCostCalculator.calculate(response).ifPresentOrElse(
                cost -> log.info("상담 의도 LLM 사용량 - memberId: {}, sessionId: {}, 걸린 시간: {}ms, 입력: {}, 출력: {}, 비용: ${}",
                        memberId, sessionId, elapsedMs, cost.inputTokens(), cost.outputTokens(),
                        String.format("%.6f", cost.costUsd())),
                () -> log.info("상담 의도 LLM 호출 - memberId: {}, sessionId: {}, 걸린 시간: {}ms (사용량 없음)",
                        memberId, sessionId, elapsedMs));
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("상담 의도 프롬프트를 읽지 못함", e);
        }
    }
}
