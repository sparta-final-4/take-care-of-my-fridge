package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 메시지의 맛 단어로 AI가 뽑은 원하는 맛·피할 맛을 한 번 더 확인할 때 쓰는 단어 (TasteWordChecker).
 * 비교는 공백을 뺀 글로 한다.
 *
 * @param spicy          매운맛 단어 ("좀 더 매운 걸로", "칼칼한 거")
 * @param sweet          단맛 단어 ("달달한 거", "단 거")
 * @param negationBefore 맛 단어 바로 앞에 오면 피하는 말 ("안 맵게", "덜 달게")
 * @param negationAfter  맛 단어 뒤 몇 글자 안에 오면 피하는 말 ("매운 거 말고", "맵지 않게", "단 거 싫어")
 */
@ConfigurationProperties(prefix = "app.chat.taste-words")
public record ChatTasteWordProperties(
        @DefaultValue({"매운", "맵게", "맵지", "매콤", "얼큰", "칼칼"})
        List<String> spicy,
        @DefaultValue({"달달", "달게", "단거"})
        List<String> sweet,
        @DefaultValue({"안", "덜"})
        List<String> negationBefore,
        @DefaultValue({"말고", "빼고", "않", "싫", "없이"})
        List<String> negationAfter
) {
}
