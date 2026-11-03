package org.example.takecareofmyfridgebe.global.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Spring AI ChatClient 설정.
 * 용도마다 시스템 프롬프트·옵션을 빈에 고정하고, 서비스에서는 @Qualifier로 골라 주입한다.
 */
@Configuration
public class ChatClientConfig {

    // 상담 답 옵션. 상담 빈에만 적용되고 후기 요약은 spring.ai.openai 기본값을 그대로 쓴다
    private static final String CHAT_REPLY_MODEL = "gpt-4o-mini";
    private static final int CHAT_REPLY_MAX_TOKENS = 800;
    // 재시도 1번을 포함해도 요청 타임아웃(spring.mvc.async.request-timeout 120s) 안에 끝나도록 짧게 둔다
    private static final Duration CHAT_REPLY_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 레시피 후기 요약용. defaultSystem 프롬프트와 토큰 비용 로깅용 CostAdvisor를 기본 적용한다.
     */
    @Bean("reviewSummaryChatClient")
    public ChatClient reviewSummaryChatClient(
            ChatClient.Builder builder,
            LlmCostCalculator llmCostCalculator
    ) {
        return builder
                .defaultSystem("너는 레시피 후기를 요약하는 어시스턴트야. 후기에 있는 내용만 쓰고 지어내지 않는다.")
                .defaultAdvisors(new CostAdvisor(llmCostCalculator, 100))
                .build();
    }

    /**
     * 요리 상담 답 스트리밍용. 시스템 프롬프트는 resources/prompts/chat-system.txt에서 읽는다.
     * 스트리밍에서는 CostAdvisor가 사용량 조각을 보지 못하므로 달지 않고, 비용은 OpenAiChatReplyGenerator가 기록한다.
     */
    @Bean("chatReplyChatClient")
    public ChatClient chatReplyChatClient(
            ChatClient.Builder builder,
            @Value("classpath:prompts/chat-system.txt") Resource systemPrompt
    ) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(CHAT_REPLY_MODEL)
                        .maxTokens(CHAT_REPLY_MAX_TOKENS)
                        .timeout(CHAT_REPLY_TIMEOUT))
                .build();
    }
}
