package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatFeatureWordProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeaturePhraseCheckerTest {

    // config/chat-words.yml app.chat.feature-words와 같은 목록
    static final ChatFeatureWordProperties WORDS = new ChatFeatureWordProperties(
            List.of("한", "하고", "하게", "고", "게", "운", "은", "는"),
            List.of("국물", "요리", "음식", "메뉴", "레시피", "거", "것", "걸"));

    // 맛 단어(taste-words)와 온도 말(temperature-words)
    private static final List<String> STEMS = FeaturePhraseChecker.stems(
            List.of("매운", "맵게", "맵지", "매콤", "얼큰", "칼칼"), List.of("달달", "달콤"), List.of("짭짤"), List.of("고소"),
            List.of("순한", "담백"), TemperatureWordCheckerTest.WORDS.warm(), TemperatureWordCheckerTest.WORDS.cold());

    @Test
    @DisplayName("맛·온도 낱말과 일반 말로만 된 표현은 특징이다 (시원한 국물, 매운 요리, 따뜻한 거, 칼칼하고 얼큰한 국물)")
    void returnsTrue_whenOnlyFeatureAndGenericWords() {
        for (String phrase : List.of("시원한 국물", "매운 요리", "따뜻한 거", "칼칼하고 얼큰한 국물", "차가운 음식")) {
            assertThat(FeaturePhraseChecker.isFeature(phrase, STEMS, WORDS)).as(phrase).isTrue();
        }
    }

    @Test
    @DisplayName("요리 이름이 들어 있으면 특징이 아니다 (매운탕, 김치찌개, 시원한 냉면, 국물)")
    void returnsFalse_whenDishNameIncluded() {
        for (String phrase : List.of("매운탕", "김치찌개", "시원한 냉면", "국물", "닭볶음탕", "김피탕")) {
            assertThat(FeaturePhraseChecker.isFeature(phrase, STEMS, WORDS)).as(phrase).isFalse();
        }
        assertThat(FeaturePhraseChecker.isFeature(null, STEMS, WORDS)).isFalse();
    }
}
