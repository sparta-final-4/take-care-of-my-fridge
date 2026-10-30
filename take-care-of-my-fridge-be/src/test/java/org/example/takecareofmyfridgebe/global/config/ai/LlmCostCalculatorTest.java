package org.example.takecareofmyfridgebe.global.config.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LlmCostCalculatorTest {

    private final LlmCostCalculator calculator = new LlmCostCalculator(0.15, 0.60);

    private ChatResponse responseWith(Usage usage) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage("요약"))),
                ChatResponseMetadata.builder().usage(usage).build()
        );
    }

    @Test
    @DisplayName("입력·출력 토큰에 100만 토큰당 단가를 곱해 비용을 계산한다")
    void calculatesCostFromUsage() {
        LlmCost cost = calculator.calculate(responseWith(new DefaultUsage(1000, 500))).orElseThrow();

        assertThat(cost.inputTokens()).isEqualTo(1000);
        assertThat(cost.outputTokens()).isEqualTo(500);
        // (1000 × 0.15 + 500 × 0.60) / 1,000,000
        assertThat(cost.costUsd()).isCloseTo(0.00045, within(1e-12));
    }

    @Test
    @DisplayName("응답이 null이거나 사용량이 없으면(EmptyUsage, 메타데이터 없이 만든 응답) 비어 있다")
    void emptyWhenUsageIsMissing() {
        assertThat(calculator.calculate(null)).isEmpty();
        assertThat(calculator.calculate(responseWith(new EmptyUsage()))).isEmpty();
        assertThat(calculator.calculate(new ChatResponse(List.of(new Generation(new AssistantMessage("요약"))))))
                .isEmpty();
    }
}
