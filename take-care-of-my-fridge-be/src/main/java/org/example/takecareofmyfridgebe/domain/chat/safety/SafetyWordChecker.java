package org.example.takecareofmyfridgebe.domain.chat.safety;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatSafetyWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatSafetyBlock;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * AI를 부르기 전에 메시지를 단어로 거른다. 스프링에 의존하지 않는 순수 클래스.
 * <ul>
 *   <li>소문자로 바꾸고 공백·숫자·기호를 뺀 글로 비교한다 ("시 1 발" → "시발", "Ignore previous" → "ignoreprevious")</li>
 *   <li>제외 목록에 있는 말(마약김밥·폭탄주·죽 먹고 등)은 먼저 지우고 본다. 지운 자리는 앞뒤 글자가 이어 붙지 않게 끊는다</li>
 *   <li>자해 → 금지어 → 탈취 순으로 본다 (자해는 안내가 달라서 먼저)</li>
 * </ul>
 */
public final class SafetyWordChecker {

    // 지운 제외 단어 자리에 넣는 글자. 비교 글에는 기호가 남지 않으므로 어떤 단어와도 이어지지 않는다
    private static final String CUT = "|";

    private SafetyWordChecker() {
    }

    /**
     * @return 걸린 종류. 걸리지 않으면 빈 값
     */
    public static Optional<ChatSafetyBlock> check(String message, ChatSafetyWordProperties words) {
        String text = normalize(message);
        for (String exclusion : words.exclusions()) {
            String word = normalize(exclusion);
            if (!word.isEmpty()) {
                text = text.replace(word, CUT);
            }
        }
        if (containsAny(text, words.selfHarm())) {
            return Optional.of(ChatSafetyBlock.SELF_HARM);
        }
        if (containsAny(text, words.banned())) {
            return Optional.of(ChatSafetyBlock.BANNED);
        }
        if (containsAny(text, words.injection())) {
            return Optional.of(ChatSafetyBlock.INJECTION);
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().map(SafetyWordChecker::normalize).anyMatch(word -> !word.isEmpty() && text.contains(word));
    }

    // 글자(한글·자모·영문 등)만 남기고 소문자로 바꾼다
    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
    }
}
