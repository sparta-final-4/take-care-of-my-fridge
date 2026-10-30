package org.example.takecareofmyfridgebe.domain.review.dto;

import org.example.takecareofmyfridgebe.global.config.ai.LlmCost;

/**
 * 후기 요약 실행 결과의 레시피별 상세.
 * reason은 건너뜀·실패 이유이며, AI를 호출하지 않았거나 사용량이 없으면 토큰·비용은 null이다.
 * 후기를 조회하기 전에 건너뛰었으면 reviewCount는 null, 성공이 아니면 newSummary는 null이다.
 */
public record ReviewSummaryItem(
        Long recipeId,
        String recipeName,
        ReviewSummaryItemStatus status,
        String reason,
        Integer reviewCount,
        Integer inputTokens,
        Integer outputTokens,
        Double costUsd,
        String previousSummary,
        String newSummary
) {

    public static ReviewSummaryItem of(
            Long recipeId,
            String recipeName,
            String previousSummary,
            ReviewSummaryItemStatus status,
            String reason,
            Integer reviewCount,
            LlmCost cost,
            String newSummary
    ) {
        return new ReviewSummaryItem(
                recipeId,
                recipeName,
                status,
                reason,
                reviewCount,
                cost == null ? null : cost.inputTokens(),
                cost == null ? null : cost.outputTokens(),
                cost == null ? null : cost.costUsd(),
                previousSummary,
                newSummary
        );
    }
}
