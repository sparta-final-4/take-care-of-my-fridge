package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishesResult;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI(Spring AI)로 요리 이름 목록을 묻는다. app.chat.reply-generator=openai일 때만 빈이 된다.
 * 종류(DishListKind)마다 시스템 프롬프트만 다르다: 비슷한 요리 prompts/chat-similar-dishes.txt, 넓은 요청 요리 prompts/chat-broad-dishes.txt.
 * 응답 모양은 SimilarDishesResult의 JSON 스키마로 강제한다(ENABLE_NATIVE_STRUCTURED_OUTPUT, strict json_schema).
 * 시스템 프롬프트는 종류마다 고정이라 OpenAI 프롬프트 캐시가 되고, 사용자 메시지는 받은 글 하나다(요리 이름 또는 요청 표현).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "openai")
public class OpenAiSimilarDishGenerator implements SimilarDishGenerator {

    private final ChatClient chatClient;
    private final LlmCostCalculator llmCostCalculator;
    private final Map<DishListKind, String> systemPrompts;

    @Autowired
    public OpenAiSimilarDishGenerator(
            @Qualifier("chatSimilarDishChatClient") ChatClient chatClient,
            LlmCostCalculator llmCostCalculator,
            @Value("classpath:prompts/chat-similar-dishes.txt") Resource similarPrompt,
            @Value("classpath:prompts/chat-broad-dishes.txt") Resource broadPrompt
    ) {
        this.chatClient = chatClient;
        this.llmCostCalculator = llmCostCalculator;
        Map<DishListKind, String> prompts = new EnumMap<>(DishListKind.class);
        prompts.put(DishListKind.SIMILAR, read(similarPrompt));
        prompts.put(DishListKind.BROAD, read(broadPrompt));
        this.systemPrompts = Collections.unmodifiableMap(prompts);
    }

    // 비슷한 요리 프롬프트만 바꿔 끼울 때 (테스트·평가). 넓은 요청 프롬프트는 기본 파일을 쓴다
    public OpenAiSimilarDishGenerator(ChatClient chatClient, LlmCostCalculator llmCostCalculator, Resource similarPrompt) {
        this(chatClient, llmCostCalculator, similarPrompt, new ClassPathResource("prompts/chat-broad-dishes.txt"));
    }

    @Override
    public List<String> generate(Long memberId, DishListKind kind, String text) {
        long startedAt = System.nanoTime();
        ResponseEntity<ChatResponse, SimilarDishesResult> response = chatClient.prompt()
                .system(systemPrompts.get(kind))
                .user(text)
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .responseEntity(SimilarDishesResult.class);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        logUsage(memberId, kind, response.response(), elapsedMs);
        SimilarDishesResult result = response.entity();
        if (result == null || result.dishes() == null) {
            return List.of();
        }
        return result.dishes();
    }

    private void logUsage(Long memberId, DishListKind kind, ChatResponse response, long elapsedMs) {
        llmCostCalculator.calculate(response).ifPresentOrElse(
                cost -> log.info("상담 {} LLM 사용량 - memberId: {}, 걸린 시간: {}ms, 입력: {}, 출력: {}, 비용: ${}",
                        kind.label(), memberId, elapsedMs, cost.inputTokens(), cost.outputTokens(), String.format("%.6f", cost.costUsd())),
                () -> log.info("상담 {} LLM 호출 - memberId: {}, 걸린 시간: {}ms (사용량 없음)", kind.label(), memberId, elapsedMs));
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("상담 요리 이름 목록 프롬프트를 읽지 못함: " + resource.getDescription(), e);
        }
    }
}
