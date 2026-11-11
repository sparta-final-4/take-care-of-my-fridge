package org.example.takecareofmyfridgebe.domain.chat.ai;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
import org.example.takecareofmyfridgebe.domain.chat.entity.ChatMessageRole;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCost;
import org.example.takecareofmyfridgebe.global.config.ai.LlmCostCalculator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

// OpenAI(Spring AI) 스트리밍으로 상담 답을 만든다. app.chat.reply-generator=openai일 때만 빈이 된다
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "openai")
public class OpenAiChatReplyGenerator implements ChatReplyGenerator {

    private static final String FINISH_REASON_LENGTH = "length";
    private static final String PROMPT_SEPARATOR = "\n\n";
    // 추천 답은 소개 한 문장 + 카드 설명 3개(각 한두 문장)라 약 200~250토큰. 넉넉히 320 (일반 답은 빈에 정한 800)
    static final int RECOMMEND_MAX_TOKENS = 320;
    // 레시피 자세히 답은 단계 최대 10줄(dev 레시피 단계 글자 합 최대 587자, 평균 217자)이라 약 300~500토큰. 넉넉히 600
    static final int RECIPE_DETAIL_MAX_TOKENS = 600;

    private final ChatClient chatClient;
    private final LlmCostCalculator llmCostCalculator;
    private final ChatReplyErrorTranslator chatReplyErrorTranslator;
    // 일반 답의 시스템 프롬프트: 공통(chat-system.txt) + 실제 추천 기준(chat-general.txt). "추천 기준이 뭐야?"에 코드 기준대로 답하게
    private final String generalSystemPrompt;
    // 추천 답의 시스템 프롬프트: 공통(chat-system.txt) + 추천 규칙(chat-recommend.txt)
    private final String recommendSystemPrompt;
    // 레시피 자세히 답의 시스템 프롬프트: 공통(chat-system.txt) + 레시피 답 규칙(chat-recipe-detail.txt)
    private final String recipeDetailSystemPrompt;

    public OpenAiChatReplyGenerator(
            @Qualifier("chatReplyChatClient") ChatClient chatClient,
            LlmCostCalculator llmCostCalculator,
            ChatReplyErrorTranslator chatReplyErrorTranslator,
            @Value("classpath:prompts/chat-system.txt") Resource systemPrompt,
            @Value("classpath:prompts/chat-recommend.txt") Resource recommendPrompt,
            @Value("classpath:prompts/chat-recipe-detail.txt") Resource recipeDetailPrompt,
            @Value("classpath:prompts/chat-general.txt") Resource generalPrompt
    ) {
        this.chatClient = chatClient;
        this.llmCostCalculator = llmCostCalculator;
        this.chatReplyErrorTranslator = chatReplyErrorTranslator;
        String common = read(systemPrompt).strip();
        this.recommendSystemPrompt = common + PROMPT_SEPARATOR + read(recommendPrompt).strip();
        this.recipeDetailSystemPrompt = common + PROMPT_SEPARATOR + read(recipeDetailPrompt).strip();
        this.generalSystemPrompt = common + PROMPT_SEPARATOR + read(generalPrompt).strip();
    }

    /**
     * 응답 조각에서 글자만 꺼내 보낸다(빈 문자열은 거른다).
     * 사용량은 OpenAI가 마지막에 따로 보내는 조각에만 있고, 나머지 조각은 0으로 온다.
     * 그래서 0보다 큰 사용량만 기억해 두었다가 스트림이 끝날 때 한 번만 비용 로그를 남긴다.
     * 오류는 첫 조각을 보냈는지에 따라 상담 에러 코드로 바꾼다.
     */
    @Override
    public Flux<String> generate(Long memberId, Long sessionId, String userMessage, List<ContextMessage> history) {
        return stream(memberId, sessionId, "상담 답", () -> {
            // 빈에 고정된 공통 시스템 프롬프트 대신 공통 + 추천 기준을 system으로 보낸다
            ChatClientRequestSpec spec = chatClient.prompt().system(generalSystemPrompt);
            // 이전 대화는 시스템 프롬프트 뒤, 이번 메시지 앞에 사용자·AI 메시지로 넣는다
            if (!history.isEmpty()) {
                spec = spec.messages(toMessages(history));
            }
            return spec.user(userMessage);
        });
    }

    private static List<Message> toMessages(List<ContextMessage> history) {
        return history.stream()
                .<Message>map(message -> message.role() == ChatMessageRole.ASSISTANT
                        ? new AssistantMessage(message.promptText())
                        : new UserMessage(message.promptText()))
                .toList();
    }

    /**
     * 추천 답. 빈에 고정된 공통 시스템 프롬프트 대신 공통 + 추천 규칙을 system으로 보내고,
     * 후보 정보는 별도 system 메시지로 사용자 메시지 앞에 넣는다. 템플릿 매개변수를 쓰지 않으므로 글이 그대로 간다.
     */
    @Override
    public Flux<String> generateRecommendation(Long memberId, Long sessionId, String userMessage, String candidateInfo) {
        return stream(memberId, sessionId, "상담 추천 답", () -> chatClient.prompt()
                .options(OpenAiChatOptions.builder().maxTokens(RECOMMEND_MAX_TOKENS))
                .system(recommendSystemPrompt)
                .messages(new SystemMessage(candidateInfo))
                .user(userMessage));
    }

    /**
     * 레시피 자세히 답. 공통 + 레시피 답 규칙을 system으로, 레시피 정보는 별도 system 메시지로 사용자 메시지 앞에 넣는다.
     */
    @Override
    public Flux<String> generateRecipeDetail(Long memberId, Long sessionId, String userMessage, String recipeInfo) {
        return stream(memberId, sessionId, "상담 레시피 자세히 답", () -> chatClient.prompt()
                .options(OpenAiChatOptions.builder().maxTokens(RECIPE_DETAIL_MAX_TOKENS))
                .system(recipeDetailSystemPrompt)
                .messages(new SystemMessage(recipeInfo))
                .user(userMessage));
    }

    private Flux<String> stream(Long memberId, Long sessionId, String label, Supplier<ChatClientRequestSpec> prompt) {
        return Flux.defer(() -> {
            AtomicBoolean firstChunkSent = new AtomicBoolean(false);
            AtomicReference<ReplyUsage> lastUsage = new AtomicReference<>();
            return prompt.get()
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        rememberUsage(response, lastUsage);
                        warnIfTruncated(memberId, sessionId, response);
                    })
                    .map(this::textOf)
                    .filter(text -> !text.isEmpty())
                    .doOnNext(text -> firstChunkSent.set(true))
                    .doOnComplete(() -> logUsage(label, memberId, sessionId, lastUsage.get()))
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

    private void logUsage(String label, Long memberId, Long sessionId, ReplyUsage usage) {
        if (usage == null) {
            log.warn("{} LLM 사용량을 받지 못함 - memberId: {}, sessionId: {}", label, memberId, sessionId);
            return;
        }
        LlmCost cost = usage.cost();
        log.info("{} LLM 사용량 - memberId: {}, sessionId: {}, 모델: {}, 입력: {}, 출력: {}, 비용: ${}",
                label, memberId, sessionId, usage.model(), cost.inputTokens(), cost.outputTokens(),
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

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("상담 프롬프트를 읽지 못함: " + resource.getDescription(), e);
        }
    }

    private record ReplyUsage(String model, LlmCost cost) {
    }
}
