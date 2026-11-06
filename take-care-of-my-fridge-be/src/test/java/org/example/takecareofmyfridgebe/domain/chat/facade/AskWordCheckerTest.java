package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAskWordProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AskWordCheckerTest {

    static final ChatAskWordProperties WORDS = new ChatAskWordProperties(
            List.of("기준", "어떻게", "왜", "방법", "얼마나", "보관", "가격", "차이", "뭐가달라", "뭐가다른"),
            List.of("추천", "뭐있", "보여줘", "먹고싶", "사고싶", "골라줘"),
            List.of("있어", "있나", "있을까", "없어", "뭐가좋아", "살만한"));

    @Test
    @DisplayName("달라는 말만 있으면 달라는 말이다 (밀키트 뭐 있어, 디저트 추천해줘, 매운 거 먹고 싶어)")
    void returnsRequest_whenOnlyRequestWord() {
        for (String message : List.of("밀키트 뭐 있어", "디저트 추천해줘", "매운 거 먹고 싶어", "밀키트 보여 줘", "하나 골라줘")) {
            assertThat(AskWordChecker.requests(message, WORDS)).as(message).isTrue();
            assertThat(AskWordChecker.asks(message, WORDS)).as(message).isFalse();
        }
    }

    @Test
    @DisplayName("묻는 말이 있으면 달라는 말이 같이 있어도 묻는 말이 이긴다 (추천 기준이 뭐야?)")
    void returnsAsk_whenQuestionWordWithRequestWord() {
        for (String message : List.of("밀키트 추천 기준이 뭐야?", "레시피 추천 기준이 뭐야?", "디저트는 어떻게 골라?",
                "매운 거 기준이 뭐야?", "두부 얼마나 보관돼?", "김치찌개 어떻게 만들어?", "밀키트랑 레시피 뭐가 달라?")) {
            assertThat(AskWordChecker.asks(message, WORDS)).as(message).isTrue();
            assertThat(AskWordChecker.requests(message, WORDS)).as(message).isFalse();
        }
    }

    @Test
    @DisplayName("밀키트와 같이 쓴 \"있어·있나·없어·뭐가 좋아\"는 달라는 말이고, 묻는 말이 같이 있으면 묻는 말이 이긴다")
    void returnsMealkitRequest_whenExistenceWord() {
        for (String message : List.of("국물 밀키트 있어?", "밀키트 있나?", "밀키트 있을까", "밀키트 없어?", "밀키트 뭐가 좋아?", "살 만한 밀키트")) {
            assertThat(AskWordChecker.requestsMealkit(message, WORDS)).as(message).isTrue();
        }
        assertThat(AskWordChecker.requestsMealkit("밀키트 추천 기준 있어?", WORDS)).isFalse();
        assertThat(AskWordChecker.requests("국물 밀키트 있어?", WORDS)).isFalse();
    }

    @Test
    @DisplayName("두 말이 모두 없으면 묻는 말도 달라는 말도 아니다 (국물 밀키트)")
    void returnsNeither_whenNoWord() {
        assertThat(AskWordChecker.asks("국물 밀키트", WORDS)).isFalse();
        assertThat(AskWordChecker.requests("국물 밀키트", WORDS)).isFalse();
        assertThat(AskWordChecker.requests(null, WORDS)).isFalse();
    }
}
