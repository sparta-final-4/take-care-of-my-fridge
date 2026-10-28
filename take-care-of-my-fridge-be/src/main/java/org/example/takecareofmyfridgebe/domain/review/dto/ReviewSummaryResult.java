package org.example.takecareofmyfridgebe.domain.review.dto;

public record ReviewSummaryResult(
        int targetCount,
        int successCount,
        int skippedCount,
        int failedCount,
        long elapsedMillis
) {
}
