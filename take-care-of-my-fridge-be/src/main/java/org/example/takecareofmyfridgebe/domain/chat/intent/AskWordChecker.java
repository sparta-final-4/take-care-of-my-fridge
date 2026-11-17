package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;

import java.util.List;

/**
 * 메시지가 묻는 말인지 본다. 스프링에 의존하지 않는 순수 클래스. 공백은 빼고 비교한다.
 * OpenAI 의도 판단이 실패했을 때 선택 재료가 있어도 묻는 말이면 추천으로 바꾸지 않는 데 쓴다 (ChatIntentFacade).
 */
public final class AskWordChecker {

    private AskWordChecker() {
    }

    // 묻는 말("기준·어떻게·왜·방법·얼마나·보관·가격·차이·뭐가 달라")이 있는지
    public static boolean asks(String message, ChatWordProperties.Ask words) {
        return containsAny(ChatTexts.removeWhitespace(message), words.question());
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(ChatTexts::removeWhitespace).anyMatch(word -> !word.isEmpty() && text.contains(word));
    }
}
