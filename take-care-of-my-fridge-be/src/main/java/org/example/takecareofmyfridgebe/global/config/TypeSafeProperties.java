package org.example.takecareofmyfridgebe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * TypeSafe(Jev 분류 모델) 호출 설정. 상담 의도 판단이 쓴다(JevIntentRunner). 키가 비면 부르지 않고 OpenAI 결과로 되돌린다.
 * API 사용법 조사: docs/testing/jev-api-notes.md
 *
 * @param baseUrl API 주소 (기본 https://api.typesafe.ai)
 * @param apiKey  API 키. 환경 변수 TYPESAFE_API_KEY에서만 받는다. 없으면 빈 값이고 호출하지 않는다. toString에 나오지 않는다
 * @param model   모델 이름. 버전 고정 이름(기본 jev-1.14.0)을 쓴다. 별칭 jev-latest는 바뀔 수 있다
 * @param timeout 연결·읽기 제한 시간
 */
@ConfigurationProperties(prefix = "app.typesafe")
public record TypeSafeProperties(
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout
) {

    static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    static final String DEFAULT_MODEL = "jev-1.14.0";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public TypeSafeProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        apiKey = apiKey == null ? "" : apiKey;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    // 키는 로그에 남지 않게 있는지만 보인다
    @Override
    public String toString() {
        return "TypeSafeProperties[baseUrl=" + baseUrl
                + ", apiKey=" + (hasApiKey() ? "(설정됨)" : "(없음)")
                + ", model=" + model + ", timeout=" + timeout + "]";
    }
}
