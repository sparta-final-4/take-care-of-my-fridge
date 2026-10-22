package org.example.takecareofmyfridgebe.domain.cooking.dto;

/**
 * 레시피 상세 화면용 요리 실패/성공 통계 응답 DTO.
 * 실패율은 화면에서 failedCount / totalCount로 직접 계산하며,
 * 요리 기록이 없는 레시피는 모든 카운트가 0으로 반환된다.
 *
 * @param recipeId     레시피 ID
 * @param totalCount   총 요리 횟수
 * @param successCount 성공 횟수
 * @param failedCount  실패 횟수
 */
public record CookingStatsResponse(
        Long recipeId,
        long totalCount,
        long successCount,
        long failedCount
) {
    public static CookingStatsResponse of(Long recipeId, CookingStatsCount count) {
        if (count == null) {
            return new CookingStatsResponse(recipeId, 0L, 0L, 0L);
        }
        return new CookingStatsResponse(
                recipeId,
                count.totalCount(),
                count.successCount(),
                count.failedCount()
        );
    }
}
