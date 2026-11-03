package org.example.takecareofmyfridgebe.domain.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 벡터 폴백에서 검색어를 임베딩할 때의 캐시와 호출 제한.
 * <br>
 * cacheTtl: 같은 검색어의 벡터를 Redis에 두는 기간
 * limitPerMinute: 서비스 전체에서 1분에 OpenAI 임베딩을 호출하는 최대 횟수. 캐시에서 꺼낸 벡터는 세지 않는다
 */
@ConfigurationProperties(prefix = "app.search.query-embedding")
public record QueryEmbeddingProperties(
        Duration cacheTtl,
        int limitPerMinute
) {}
