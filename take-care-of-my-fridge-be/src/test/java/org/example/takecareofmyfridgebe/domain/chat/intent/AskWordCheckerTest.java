package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AskWordCheckerTest {

    public static final ChatWordProperties.Ask WORDS = new ChatWordProperties.Ask(
            List.of("기준", "어떻게", "왜", "방법", "얼마나", "보관", "가격", "차이", "뭐가달라", "뭐가다른",
                    "먹었어", "먹었니", "먹었냐", "했어", "했니", "먹어도돼", "괜찮", "영양소", "영양성분", "효능",
                    "뭐야", "뭐예요", "뭐에요", "뭔데"),
            List.of("추천", "뭐있", "보여줘", "먹고싶", "사고싶", "골라줘"),
            List.of("있어", "있나", "있을까", "없어", "뭐가좋아", "살만한"),
            List.of("안녕", "반가워", "반갑", "고마워", "고맙", "감사", "잘지내", "잘자", "좋은아침"),
            List.of("레시피", "만드는법", "만들기", "요리법", "조리법"));

    @Test
    @DisplayName("묻는 말이 있으면 달라는 말이 같이 있어도 묻는 말로 본다 (추천 기준이 뭐야?, 두부 얼마나 보관돼?)")
    void returnsAsk_whenQuestionWord() {
        for (String message : List.of("밀키트 추천 기준이 뭐야?", "레시피 추천 기준이 뭐야?", "디저트는 어떻게 골라?",
                "매운 거 기준이 뭐야?", "두부 얼마나 보관돼?", "김치찌개 어떻게 만들어?", "밀키트랑 레시피 뭐가 달라?", "밥 먹었어?")) {
            assertThat(AskWordChecker.asks(message, WORDS)).as(message).isTrue();
        }
    }

    @Test
    @DisplayName("묻는 말이 없으면 묻는 말이 아니다 (밀키트 뭐 있어, 국물 밀키트)")
    void returnsNotAsk_whenNoQuestionWord() {
        for (String message : List.of("밀키트 뭐 있어", "디저트 추천해줘", "국물 밀키트")) {
            assertThat(AskWordChecker.asks(message, WORDS)).as(message).isFalse();
        }
    }

    @Test
    @DisplayName("묻는 말이 줄바꿈으로 나뉘어 있어도 묻는 말로 본다 (뭐가\n달라)")
    void returnsAsk_whenQuestionWordSplitByNewline() {
        assertThat(AskWordChecker.asks("김치찌개랑 된장찌개 뭐가\n달라?", WORDS)).isTrue();
    }
}
