package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 묻는 말·달라는 말 단어 (AskWordChecker). 비교는 공백을 뺀 글로 한다.
 * 서버가 AI 의도를 추천으로 바꿀 때는 달라는 말이 있고 묻는 말이 없을 때만 바꾼다.
 *
 * @param question 묻는 말 ("추천 기준이 뭐야?", "두부 얼마나 보관돼?")
 * @param request  달라는 말 ("밀키트 추천해줘", "뭐 있어", "먹고 싶어")
 */
@ConfigurationProperties(prefix = "app.chat.ask-words")
public record ChatAskWordProperties(
        @DefaultValue({"기준", "어떻게", "왜", "방법", "얼마나", "보관", "가격", "차이", "뭐가달라", "뭐가다른"})
        List<String> question,
        @DefaultValue({"추천", "뭐있", "보여줘", "먹고싶", "사고싶", "골라줘"})
        List<String> request
) {
}
