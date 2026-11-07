package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatHealthWordProperties;

/**
 * 사용자가 보낸 말에 건강 관련 말(질환·알레르기·임신·약)이 있는지 본다. 스프링에 의존하지 않는 순수 클래스.
 * 공백을 뺀 글로 비교하고, 제외 목록에 있는 말("약불", "약간", "신장개업")은 먼저 지우고 본다.
 * 지운 자리는 끊어서 앞뒤 글자가 새 단어로 붙지 않게 한다. AI 답은 보지 않는다.
 */
public final class HealthWordChecker {

    // 지운 제외 단어 자리에 넣는 글자. 공백을 뺀 글에서 건강 말과 이어지지 않는다
    private static final String CUT = "|";

    private HealthWordChecker() {
    }

    public static boolean mentions(String message, ChatHealthWordProperties words) {
        String text = compact(message);
        if (text.isEmpty()) {
            return false;
        }
        for (String exclusion : words.exclusions()) {
            String word = compact(exclusion);
            if (!word.isEmpty()) {
                text = text.replace(word, CUT);
            }
        }
        String checked = text;
        return words.health().stream().map(HealthWordChecker::compact)
                .anyMatch(word -> !word.isEmpty() && checked.contains(word));
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
