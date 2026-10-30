package org.example.takecareofmyfridgebe.global.config.ai;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 응답의 Usage 메타데이터와 app.llm 단가(100만 토큰당 USD)로 LLM 호출 비용을 계산한다.
 * CostAdvisor 로그와 후기 요약 실행 결과가 같은 계산을 쓰도록 한 곳에 둔다.
 */
@Component
public class LlmCostCalculator {

    private final double inputPer1m;
    private final double outputPer1m;

    public LlmCostCalculator(
            @Value("${app.llm.input-per-1m}") double inputPer1m,
            @Value("${app.llm.output-per-1m}") double outputPer1m
    ) {
        this.inputPer1m = inputPer1m;
        this.outputPer1m = outputPer1m;
    }

    /**
     * 응답이 없거나(null) 사용량을 제공하지 않으면(EmptyUsage) 비어 있다.
     * Spring AI는 메타데이터·Usage·토큰 수를 null로 주지 않고, 사용량이 없으면 EmptyUsage를 준다.
     */
    public Optional<LlmCost> calculate(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return Optional.empty();
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage instanceof EmptyUsage) {
            return Optional.empty();
        }

        int inputTokens = usage.getPromptTokens();
        int outputTokens = usage.getCompletionTokens();
        double cost = (inputTokens * inputPer1m + outputTokens * outputPer1m) / 1_000_000.0;
        return Optional.of(new LlmCost(inputTokens, outputTokens, cost));
    }
}
