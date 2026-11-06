package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAskWordProperties;

import java.util.List;

/**
 * 메시지가 묻는 말인지, 달라는 말인지 본다. 스프링에 의존하지 않는 순수 클래스.
 * 서버가 AI 의도를 바꾸는 규칙(일반 대화 → 추천)은 이 확인을 거친다: 달라는 말이 있고 묻는 말이 없을 때만 바꾼다.
 * "추천 기준이 뭐야?"처럼 달라는 말("추천")과 묻는 말("기준")이 같이 있으면 묻는 말이 이긴다. 공백은 빼고 비교한다.
 */
public final class AskWordChecker {

    private AskWordChecker() {
    }

    // 묻는 말("기준·어떻게·왜·방법·얼마나·보관·가격·차이·뭐가 달라")이 있는지
    public static boolean asks(String message, ChatAskWordProperties words) {
        return containsAny(compact(message), words.question());
    }

    // 달라는 말("추천·뭐 있·보여줘·먹고 싶·사고 싶·골라줘")이 있고 묻는 말이 없는지
    public static boolean requests(String message, ChatAskWordProperties words) {
        String text = compact(message);
        return containsAny(text, words.request()) && !containsAny(text, words.question());
    }

    // 인사 말("안녕·반가워·고마워·잘 지내")이 있는지
    public static boolean greets(String message, ChatAskWordProperties words) {
        return containsAny(compact(message), words.greeting());
    }

    // 밀키트와 같이 쓴 말: 달라는 말에 "있어·있나·없어·뭐가 좋아·살 만한"도 더하고, 묻는 말이 없는지 ("국물 밀키트 있어?")
    public static boolean requestsMealkit(String message, ChatAskWordProperties words) {
        String text = compact(message);
        return (containsAny(text, words.request()) || containsAny(text, words.mealkitRequest()))
                && !containsAny(text, words.question());
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(AskWordChecker::compact).anyMatch(word -> !word.isEmpty() && text.contains(word));
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\s+", "");
    }
}
