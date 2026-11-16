package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishesResult;
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
 * OpenAI(Spring AI)로 요리 이름과 비슷한 요리 이름을 묻는다. app.chat.reply-generator=openai일 때만 빈이 된다.
 * 응답 모양은 SimilarDishesResult의 JSON 스키마로 강제한다(ENABLE_NATIVE_STRUCTURED_OUTPUT, strict json_schema).
 * 시스템 프롬프트는 고정(prompts/chat-similar-dishes.txt)이라 OpenAI 프롬프트 캐시가 되고, 사용자 메시지는 요리 이름 하나다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "openai")
public class OpenAiSimilarDishGenerator implements SimilarDishGenerator {

    private final ChatClient chatClient;
    private final LlmCostCalculator llmCostCalculator;
    private final String systemPrompt;

    public OpenAiSimilarDishGenerator(
            @Qualifier("chatSimilarDishChatClient") ChatClient chatClient,
            LlmCostCalculator llmCostCalculator,
            @Value("classpath:prompts/chat-similar-dishes.txt") Resource prompt
    ) {
        this.chatClient = chatClient;
        this.llmCostCalculator = llmCostCalculator;
        this.systemPrompt = read(prompt);
    }

    @Override
    public List<String> generate(Long memberId, String dishText) {
        long startedAt = System.nanoTime();
        ResponseEntity<ChatResponse, SimilarDishesResult> response = chatClient.prompt()
                .system(systemPrompt)
                .user(dishText)
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .responseEntity(SimilarDishesResult.class);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        logUsage(memberId, response.response(), elapsedMs);
        SimilarDishesResult result = response.entity();
        if (result == null || result.dishes() == null) {
            return List.of();
        }
        return result.dishes();
    }

    private void logUsage(Long memberId, ChatResponse response, long elapsedMs) {
        llmCostCalculator.calculate(response).ifPresentOrElse(
                cost -> log.info("상담 비슷한 요리 LLM 사용량 - memberId: {}, 걸린 시간: {}ms, 입력: {}, 출력: {}, 비용: ${}",
                        memberId, elapsedMs, cost.inputTokens(), cost.outputTokens(), String.format("%.6f", cost.costUsd())),
                () -> log.info("상담 비슷한 요리 LLM 호출 - memberId: {}, 걸린 시간: {}ms (사용량 없음)", memberId, elapsedMs));
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("상담 비슷한 요리 프롬프트를 읽지 못함", e);
        }
    }
}
