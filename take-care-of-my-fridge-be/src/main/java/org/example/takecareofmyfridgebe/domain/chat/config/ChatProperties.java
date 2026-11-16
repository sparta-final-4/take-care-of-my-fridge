package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        int dailyLimit,
        Duration sessionTimeout,
        // 한 상담의 답 받기 잠금 유지 시간. 요청 타임아웃보다 길게 두어, 해제를 못 한 경우의 안전망으로만 쓴다
        Duration replyLockTtl,
        // 요리 이름별 비슷한 요리 이름 캐시 유지 시간 (SimilarDishStore). 설정이 없으면 7일
        Duration similarDishesTtl
) {

    static final Duration DEFAULT_SIMILAR_DISHES_TTL = Duration.ofDays(7);

    public ChatProperties {
        similarDishesTtl = similarDishesTtl == null ? DEFAULT_SIMILAR_DISHES_TTL : similarDishesTtl;
    }
}
