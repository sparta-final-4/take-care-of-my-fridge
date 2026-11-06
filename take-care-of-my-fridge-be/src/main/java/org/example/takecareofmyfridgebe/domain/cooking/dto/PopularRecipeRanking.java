package org.example.takecareofmyfridgebe.domain.cooking.dto;

import java.util.List;

/**
 * 인기 레시피 순위 (캐시에 담는 값). 레시피 이름·이미지·평점은 담지 않고 조회할 때마다 읽는다.
 * Redis 캐시에서 목록을 그대로 꺼내면 역직렬화에 실패하므로 record로 감싼다 (CacheConfig 참고).
 *
 * @param counts 최근 30일 요리한 회원 수 순 레시피 (많은 순)
 */
public record PopularRecipeRanking(
        List<PopularRecipeCookingCount> counts
) {
    public PopularRecipeRanking {
        counts = counts == null ? List.of() : List.copyOf(counts);
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }
}
