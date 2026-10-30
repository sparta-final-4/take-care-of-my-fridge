package org.example.takecareofmyfridgebe.global.config.ai;

/**
 * LLM 호출 1회의 토큰 사용량과 예상 비용(USD).
 */
public record LlmCost(
        int inputTokens,
        int outputTokens,
        double costUsd
) {
}
