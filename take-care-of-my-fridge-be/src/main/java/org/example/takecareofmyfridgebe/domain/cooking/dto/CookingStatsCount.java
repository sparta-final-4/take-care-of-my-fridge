package org.example.takecareofmyfridgebe.domain.cooking.dto;

/**
 * 레시피 요리 통계 QueryDSL 집계 결과 매핑용 DTO.
 * 집계 함수(count, sum)의 결과가 null일 경우 0L로 보정한다.
 *
 * @param totalCount   총 요리 횟수
 * @param successCount 성공 횟수
 * @param failedCount  실패 횟수
 */
public record CookingStatsCount(
        Long totalCount,
        Long successCount,
        Long failedCount
) {
    public CookingStatsCount {
        totalCount = totalCount != null ? totalCount : 0L;
        successCount = successCount != null ? successCount : 0L;
        failedCount = failedCount != null ? failedCount : 0L;
    }

    public static CookingStatsCount empty() {
        return new CookingStatsCount(0L, 0L, 0L);
    }
}
