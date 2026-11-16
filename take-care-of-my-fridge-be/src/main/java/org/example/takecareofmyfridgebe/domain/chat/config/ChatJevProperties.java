package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Jev(TypeSafe 분류 전용 모델) 의도 판단 설정. 그림자 모드: 켜도 결과는 로그로만 남기고 의도 판단에 쓰지 않는다.
 * API 사용법 조사: docs/testing/jev-api-notes.md
 *
 * @param enabled 켤지 (기본 꺼짐)
 * @param baseUrl API 주소 (기본 https://api.typesafe.ai)
 * @param apiKey  API 키. 환경 변수 TYPESAFE_API_KEY에서만 받는다. 없으면 빈 값. toString에 나오지 않는다
 * @param model   모델 이름. jev-latest·jev-preview만 있고 버전 고정 이름은 없다
 * @param timeout 호출 제한 시간
 */
@ConfigurationProperties(prefix = "app.chat.jev")
public record ChatJevProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout
) {

    static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    static final String DEFAULT_MODEL = "jev-latest";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public ChatJevProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        apiKey = apiKey == null ? "" : apiKey;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    // 키는 로그에 남지 않게 있는지만 보인다
    @Override
    public String toString() {
        return "ChatJevProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
                + ", apiKey=" + (apiKey.isBlank() ? "(없음)" : "(설정됨)")
                + ", model=" + model + ", timeout=" + timeout + "]";
    }
}
