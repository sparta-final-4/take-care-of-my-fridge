package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상담 의도 판단에 Jev(TypeSafe 분류 전용 모델)를 그림자 모드로 쓸지. 켜도 결과는 비교 로그로만 남기고 의도 판단에 쓰지 않는다.
 * 호출 설정(주소·키·모델·제한 시간)은 global/config/TypeSafeProperties(app.typesafe)에 있다.
 *
 * @param enabled 켤지 (기본 꺼짐, 환경 변수 CHAT_JEV_ENABLED)
 */
@ConfigurationProperties(prefix = "app.chat.jev")
public record ChatJevProperties(
        boolean enabled
) {
}
