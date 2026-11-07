package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteWordProperties;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 메시지의 맛 단어로 원하는 맛·피할 맛을 한 번 더 정한다. 스프링에 의존하지 않는 순수 클래스.
 * AI가 "좀 더 매운 걸로"를 피할 맛으로 넣는 것처럼 반대로 뽑는 경우를 서버가 바로잡는다.
 * <ul>
 *   <li>맛 단어(매운·맵게·칼칼 / 달달·달게·단 거)가 있고 부정어가 없으면 원하는 맛, 있으면 피할 맛</li>
 *   <li>부정어는 위치를 본다: "안·덜"은 맛 단어 바로 앞("안 맵게", "덜 달게"), "말고·빼고·않·싫·없이"는 맛 단어 뒤 {@value #AFTER_WINDOW}글자 안
 *       ("매운 거 말고", "맵지 않게"). 그래서 "30분 안에 매운 거"나 "짭짤한 거 말고 매운 걸로"는 원하는 맛이다</li>
 *   <li>공백은 빼고 비교한다 ("단 거" = "단거")</li>
 * </ul>
 */
public final class TasteWordChecker {

    // 맛 단어 뒤에서 부정어를 찾을 글자 수 (공백 뺀 글 기준, "매운거말고" → 맛 단어 뒤 "거말고")
    static final int AFTER_WINDOW = 4;

    private TasteWordChecker() {
    }

    // 메시지에서 그 맛을 원하는지(true) 피하는지(false). 맛 단어가 없으면 빈 값
    public enum Stance { WANT, AVOID }

    /**
     * @return 맛마다 원하는지·피하는지. 맛 단어가 없는 맛은 담지 않는다
     */
    public static List<TasteStance> check(String message, ChatTasteWordProperties words) {
        String text = compact(message);
        List<TasteStance> result = new ArrayList<>();
        stance(text, words.spicy(), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.SPICY, stance)));
        stance(text, words.sweet(), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.SWEET, stance)));
        return result;
    }

    public record TasteStance(Member.Taste taste, Stance stance) {
    }

    /**
     * 메시지에 나온 맛 전체(매운·단·짠·감칠·순한)의 원함·피함. 부정어 규칙은 check와 같다 ("짠 거 말고" → 짠맛 피함, "담백한 거로" → 순한맛 원함).
     * 다시 추천에서 맛이 부딪힐 때 이번 말에 나온 쪽을 따르는 데 쓴다 (check는 매운맛·단맛만 AI 결과를 고친다).
     *
     * @return 맛마다 원하는지·피하는지. 맛 단어가 없는 맛은 담지 않는다
     */
    public static List<TasteStance> stances(String message, ChatTasteWordProperties words) {
        String text = compact(message);
        List<TasteStance> result = new ArrayList<>();
        stance(text, words.spicy(), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.SPICY, stance)));
        stance(text, words.sweet(), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.SWEET, stance)));
        stance(text, orEmpty(words.salty()), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.SALTY, stance)));
        stance(text, orEmpty(words.umami()), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.UMAMI, stance)));
        stance(text, orEmpty(words.mild()), words).ifPresent(stance -> result.add(new TasteStance(Member.Taste.MILD, stance)));
        return result;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 메시지에 단어가 나온 맛 (원하든 피하든). 새 추천에서 이번 말에 없는 맛을 조건에서 뺄 때 쓴다.
     * 매운맛·단맛은 맛 단어, 짠맛·감칠맛·순한맛은 그 맛을 말하는 단어로 본다.
     */
    public static Set<Member.Taste> mentioned(String message, ChatTasteWordProperties words) {
        String text = compact(message);
        Set<Member.Taste> tastes = EnumSet.noneOf(Member.Taste.class);
        addIfAny(tastes, text, Member.Taste.SPICY, words.spicy());
        addIfAny(tastes, text, Member.Taste.SWEET, words.sweet());
        addIfAny(tastes, text, Member.Taste.SALTY, words.salty());
        addIfAny(tastes, text, Member.Taste.UMAMI, words.umami());
        addIfAny(tastes, text, Member.Taste.MILD, words.mild());
        return tastes;
    }

    private static void addIfAny(Set<Member.Taste> tastes, String text, Member.Taste taste, List<String> tasteWords) {
        if (tasteWords != null && tasteWords.stream().map(TasteWordChecker::compact)
                .anyMatch(word -> !word.isEmpty() && text.contains(word))) {
            tastes.add(taste);
        }
    }

    // 맛 단어가 하나라도 부정어와 붙어 있으면 피함, 맛 단어만 있으면 원함
    private static Optional<Stance> stance(String text, List<String> tasteWords, ChatTasteWordProperties words) {
        boolean found = false;
        for (String word : tasteWords) {
            String tasteWord = compact(word);
            if (tasteWord.isEmpty()) {
                continue;
            }
            int at = text.indexOf(tasteWord);
            while (at >= 0) {
                found = true;
                if (negatedBefore(text.substring(0, at), words) || negatedAfter(text, at + tasteWord.length(), words)) {
                    return Optional.of(Stance.AVOID);
                }
                at = text.indexOf(tasteWord, at + 1);
            }
        }
        return found ? Optional.of(Stance.WANT) : Optional.empty();
    }

    // 단어 바로 앞에 부정어("안·덜")가 붙었는지. 분류 단어 확인(CategoryWordChecker)도 같은 기준을 쓴다
    static boolean negatedBefore(String before, ChatTasteWordProperties words) {
        return words.negationBefore().stream().map(TasteWordChecker::compact)
                .anyMatch(negation -> !negation.isEmpty() && before.endsWith(negation));
    }

    // 단어 뒤 AFTER_WINDOW글자 안에 부정어("말고·빼고·않·싫·없이")가 있는지
    static boolean negatedAfter(String text, int from, ChatTasteWordProperties words) {
        String after = text.substring(from, Math.min(text.length(), from + AFTER_WINDOW));
        return words.negationAfter().stream().map(TasteWordChecker::compact)
                .anyMatch(negation -> !negation.isEmpty() && after.contains(negation));
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
