package org.example.takecareofmyfridgebe.global.config.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;

/**
 * LLM 호출 시 토큰 사용량과 예상 비용을 로깅하는 Advisor.
 * 응답의 Usage 메타데이터가 있으면 설정된 100만 토큰당 단가를 바탕으로 비용을 계산하여 로그로 남긴다.
 */
public class CostAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CostAdvisor.class);

    private final double inputPer1m;
    private final double outputPer1m;
    private final int order;

    public CostAdvisor(double inputPer1m, double outputPer1m, int order) {
        this.inputPer1m = inputPer1m;
        this.outputPer1m = outputPer1m;
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
        if (chatResponse == null || chatResponse.getMetadata().getUsage() == null) {
            return response; // 사용량을 제공하지 않는 경우 통과
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        double cost = (usage.getPromptTokens() * inputPer1m
                + usage.getCompletionTokens() * outputPer1m) / 1_000_000.0;

        log.info("LLM 호출 — 입력={}, 출력={}, 합={}, 비용=${}",
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                String.format("%.6f", cost));

        var result = chatResponse.getResult();
        if (result != null && result.getOutput() != null) {
            var output = result.getOutput();
            if (output.hasToolCalls()) {
                output.getToolCalls().forEach(tc ->
                        log.info("  ↳ 모델이 요청한 도구: {} {}", tc.name(), tc.arguments()));
            }
        }

        return response;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
