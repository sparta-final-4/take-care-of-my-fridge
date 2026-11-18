package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상담 의도 판단에 Jev(TypeSafe 분류 전용 모델)를 어떻게 쓸지.
 * 호출 설정(주소·키·모델·제한 시간)은 global/config/TypeSafeProperties(app.typesafe)에 있다.
 *
 * @param mode off(부르지 않음) / shadow(결과는 비교 로그로만) / on(Jev 판단으로 의도를 정함). 기본 off, 환경 변수 CHAT_JEV_MODE
 */
@ConfigurationProperties(prefix = "app.chat.jev")
public record ChatJevProperties(
        Mode mode
) {

    public static final ChatJevProperties OFF = new ChatJevProperties(Mode.OFF);

    public ChatJevProperties {
        mode = mode == null ? Mode.OFF : mode;
    }

    public enum Mode {
        // Jev를 부르지 않는다
        OFF,
        // OpenAI 의도 판단과 함께 Jev를 부르고, 결과는 비교 로그로만 남긴다
        SHADOW,
        // Jev 판단으로 의도를 정한다. Jev가 실패하거나 의도 confidence가 낮으면 OpenAI 결과로 되돌린다
        ON
    }

    // Jev를 부르는지 (shadow·on)
    public boolean calls() {
        return mode != Mode.OFF;
    }

    public boolean on() {
        return mode == Mode.ON;
    }
}
