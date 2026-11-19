package org.example.takecareofmyfridgebe.global.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
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

    // 상담 꺼내기(요리 이름·키워드·재료) 옵션. 짧은 JSON만 받으므로 출력 토큰과 시간 제한을 작게 두고 재시도하지 않는다.
    // 실패하면 다시 부르지 않고 서버 답으로 간다(ChatIntentFacade). 재시도 수는 호출 옵션이 아니라 OpenAI 클라이언트에 정해지므로
    // 꺼내기 전용 모델을 따로 만든다 (intentChatModel)
    static final String CHAT_INTENT_MODEL = "gpt-4o-mini";
    static final double CHAT_INTENT_TEMPERATURE = 0.0;
    static final int CHAT_INTENT_MAX_TOKENS = 200;
    static final Duration CHAT_INTENT_TIMEOUT = Duration.ofSeconds(10);
    static final int CHAT_INTENT_MAX_RETRIES = 0;

    // 비슷한 요리 묻기 옵션. 요리 이름 3~5개만 받으므로 출력 토큰을 작게, 추천 답이 늦어지지 않게 타임아웃을 짧게 둔다
    private static final String CHAT_SIMILAR_DISH_MODEL = "gpt-4o-mini";
    private static final double CHAT_SIMILAR_DISH_TEMPERATURE = 0.0;
    private static final int CHAT_SIMILAR_DISH_MAX_TOKENS = 100;
    private static final Duration CHAT_SIMILAR_DISH_TIMEOUT = Duration.ofSeconds(5);

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

    /**
     * 상담 메시지에서 요리 이름·키워드·재료 꺼내기용 (스트리밍 없음). 시스템 프롬프트는 OpenAiChatIntentExtractor가 prompts/chat-intent.txt에서 읽는다.
     * 같은 메시지에 같은 결과가 나오도록 temperature를 0으로 두고, 토큰 비용은 CostAdvisor가 기록한다.
     * 시간 제한 10초, 재시도 없는 꺼내기 전용 모델(intentChatModel)을 쓴다. 다른 호출은 기본 모델 그대로다.
     */
    @Bean("chatIntentChatClient")
    public ChatClient chatIntentChatClient(
            OpenAiChatModel openAiChatModel,
            LlmCostCalculator llmCostCalculator,
            @Value("${spring.ai.openai.api-key}") String openAiApiKey
    ) {
        return ChatClient.builder(intentChatModel(openAiChatModel.getOptions(), openAiApiKey))
                .defaultAdvisors(new CostAdvisor(llmCostCalculator, 100))
                .build();
    }

    /**
     * 꺼내기 전용 모델: 기본 모델(spring.ai.openai)과 같은 키에 꺼내기 옵션과 시간 제한 {@code CHAT_INTENT_TIMEOUT}(10초),
     * 재시도 {@value #CHAT_INTENT_MAX_RETRIES}번을 둔다. 기본 모델의 옵션(시간 제한 60초, 재시도 1번)은 바꾸지 않는다.
     * <p>
     * 키는 따로 받아 넣는다. 자동 설정은 키를 OpenAI 클라이언트에만 넣고 기본 모델 옵션에는 넣지 않아서,
     * 옵션만 복사하면 키가 없다. 키 없이 만들면 Spring AI가 OS 환경변수 OPENAI_API_KEY만 찾으므로,
     * 키가 Parameter Store(Spring 속성)에만 있는 배포 서버에서는 시작할 때 실패한다.
     */
    static OpenAiChatModel intentChatModel(OpenAiChatOptions baseOptions, String apiKey) {
        OpenAiChatOptions options = baseOptions.mutate()
                .apiKey(apiKey)
                .model(CHAT_INTENT_MODEL)
                .temperature(CHAT_INTENT_TEMPERATURE)
                .maxTokens(CHAT_INTENT_MAX_TOKENS)
                .timeout(CHAT_INTENT_TIMEOUT)
                .maxRetries(CHAT_INTENT_MAX_RETRIES)
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    /**
     * 상담 추천에서 요리 이름과 비슷한 요리 묻기용 (스트리밍 없음). 시스템 프롬프트는 OpenAiSimilarDishGenerator가 prompts/chat-similar-dishes.txt에서 읽는다.
     * 같은 요리 이름에 같은 결과가 나오도록 temperature를 0으로 두고, 토큰 비용은 의도 뽑기와 같이 CostAdvisor가 기록한다.
     */
    @Bean("chatSimilarDishChatClient")
    public ChatClient chatSimilarDishChatClient(
            ChatClient.Builder builder,
            LlmCostCalculator llmCostCalculator
    ) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(CHAT_SIMILAR_DISH_MODEL)
                        .temperature(CHAT_SIMILAR_DISH_TEMPERATURE)
                        .maxTokens(CHAT_SIMILAR_DISH_MAX_TOKENS)
                        .timeout(CHAT_SIMILAR_DISH_TIMEOUT))
                .defaultAdvisors(new CostAdvisor(llmCostCalculator, 100))
                .build();
    }
}
