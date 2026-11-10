package org.example.takecareofmyfridgebe.domain.cooking.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeRanking;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.global.config.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 인기 레시피 순위(최근 30일 요리한 회원 수 순 레시피 ID). 30일 요리 기록을 모으는 무거운 집계라 24시간 동안 캐시한다.
 * 순위는 30일 집계라 최대 24시간 늦게 바뀌어도 괜찮다는 정책이다. 레시피 이름·이미지·평점은 여기 담지 않는다 (PopularRecipeService가 매번 읽는다).
 * 요리 기록이 없는 빈 순위는 캐시하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularRecipeRankingService {

    static final int DAYS_SPAN = 30;

    private final CookingLogRepository cookingLogRepository;

    @Cacheable(
            cacheNames = CacheNames.POPULAR_RECIPE_RANKING,
            key = "#size",
            unless = "#result == null || #result.isEmpty()"
    )
    public PopularRecipeRanking getRanking(int size) {
        LocalDateTime since = LocalDateTime.now().minusDays(DAYS_SPAN);
        return new PopularRecipeRanking(cookingLogRepository.findPopularCookingCounts(since, size));
    }
}
