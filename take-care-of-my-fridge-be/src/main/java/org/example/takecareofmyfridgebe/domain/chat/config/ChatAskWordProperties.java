package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 묻는 말·달라는 말 단어 (AskWordChecker). 비교는 공백을 뺀 글로 한다.
 * 서버가 AI 의도를 추천으로 바꿀 때는 달라는 말이 있고 묻는 말이 없을 때만 바꾼다.
 *
 * @param question       묻는 말 ("추천 기준이 뭐야?", "두부 얼마나 보관돼?", 지난 일을 묻는 "밥 먹었어?")
 * @param request        달라는 말 ("밀키트 추천해줘", "뭐 있어", "먹고 싶어")
 * @param mealkitRequest 밀키트와 같이 있을 때만 달라는 말로 보는 말 ("국물 밀키트 있어?", "밀키트 뭐가 좋아?")
 * @param greeting       인사 말 ("안녕", "고마워"). 있으면 이름만 말한 짧은 말이어도 추천으로 바꾸지 않는다 ("안녕 밥은?")
 */
@ConfigurationProperties(prefix = "app.chat.ask-words")
public record ChatAskWordProperties(
        @DefaultValue({"기준", "어떻게", "왜", "방법", "얼마나", "보관", "가격", "차이", "뭐가달라", "뭐가다른",
                "먹었어", "먹었니", "먹었냐", "했어", "했니", "먹어도돼", "괜찮", "영양소", "영양성분", "효능"})
        List<String> question,
        @DefaultValue({"추천", "뭐있", "보여줘", "먹고싶", "사고싶", "골라줘"})
        List<String> request,
        @DefaultValue({"있어", "있나", "있을까", "없어", "뭐가좋아", "살만한"})
        List<String> mealkitRequest,
        @DefaultValue({"안녕", "반가워", "반갑", "고마워", "고맙", "감사", "잘지내", "잘자", "좋은아침"})
        List<String> greeting
) {
}
