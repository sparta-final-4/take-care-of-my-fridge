package org.example.takecareofmyfridgebe.global.config.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

/**
 * LLM 호출 시 토큰 사용량과 예상 비용을 로깅하는 Advisor.
 * 응답의 Usage 메타데이터가 있으면 LlmCostCalculator로 비용을 계산하여 로그로 남긴다.
 */
public class CostAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CostAdvisor.class);

    private final LlmCostCalculator costCalculator;
    private final int order;

    public CostAdvisor(LlmCostCalculator costCalculator, int order) {
        this.costCalculator = costCalculator;
        this.order = order;
    }

    // 요청이 나갈 때: 그대로 통과
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    // 응답이 돌아올 때: 토큰 사용량과 비용 로깅
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        var chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return response;
        }

        // 사용량을 제공하지 않는 경우 통과
        costCalculator.calculate(chatResponse).ifPresent(cost -> {
            log.info("LLM 호출 — 입력={}, 출력={}, 합={}, 비용=${}",
                    cost.inputTokens(),
                    cost.outputTokens(),
                    cost.inputTokens() + cost.outputTokens(),
                    String.format("%.6f", cost.costUsd()));

            var result = chatResponse.getResult();
            if (result != null && result.getOutput().hasToolCalls()) {
                result.getOutput().getToolCalls().forEach(tc ->
                        log.info("  ↳ 모델이 요청한 도구: {} {}", tc.name(), tc.arguments()));
            }
        });

        return response;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
