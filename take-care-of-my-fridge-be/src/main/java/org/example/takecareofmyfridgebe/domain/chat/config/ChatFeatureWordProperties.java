package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 특징 표현 판단 단어 (FeaturePhraseChecker). "시원한 국물"처럼 맛·온도 낱말과 일반 말로만 된 표현은 요리 이름이 아니다.
 *
 * @param endings 맛·온도 단어 뒤에 붙어도 특징 낱말인 끝말 ("시원" + "한", "매운" + "")
 * @param generic 특징 낱말과 같이 있어도 요리 이름이 아닌 일반 말 ("국물", "요리")
 */
@ConfigurationProperties(prefix = "app.chat.feature-words")
public record ChatFeatureWordProperties(
        List<String> endings,
        List<String> generic
) {

    public ChatFeatureWordProperties {
        endings = endings == null ? List.of() : List.copyOf(endings);
        generic = generic == null ? List.of() : List.copyOf(generic);
    }
}
