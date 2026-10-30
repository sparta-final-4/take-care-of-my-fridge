package org.example.takecareofmyfridgebe.domain.review.dto;

import java.util.List;
import java.util.Objects;

/**
 * 후기 요약 1회 실행 결과. 저장하지 않고 응답(관리자 실행)과 로그(새벽 실행)로만 쓴다.
 * 대상 레시피마다 items에 한 줄씩 들어가므로 대상 = 성공 + 건너뜀 + 실패다.
 * 토큰·비용 합계는 사용량이 있는 레시피만 더한다.
 */
public record ReviewSummaryResult(
        int targetCount,
        int successCount,
        int skippedCount,
        int failedCount,
        long elapsedMillis,
        long totalInputTokens,
        long totalOutputTokens,
        double totalCostUsd,
        List<ReviewSummaryItem> items
) {

    public static ReviewSummaryResult of(List<ReviewSummaryItem> items, long elapsedMillis) {
        return new ReviewSummaryResult(
                items.size(),
                count(items, ReviewSummaryItemStatus.SUCCESS),
                count(items, ReviewSummaryItemStatus.SKIPPED),
                count(items, ReviewSummaryItemStatus.FAILED),
                elapsedMillis,
                items.stream().map(ReviewSummaryItem::inputTokens).filter(Objects::nonNull)
                        .mapToLong(Integer::longValue).sum(),
                items.stream().map(ReviewSummaryItem::outputTokens).filter(Objects::nonNull)
                        .mapToLong(Integer::longValue).sum(),
                items.stream().map(ReviewSummaryItem::costUsd).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).sum(),
                items
        );
    }

    private static int count(List<ReviewSummaryItem> items, ReviewSummaryItemStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }
}
