package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
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
 *   <li>부정어는 위치를 본다: "안·덜"은 맛 단어 바로 앞("안 맵게", "덜 달게"), "말고·빼고·빼줘·제외·않·싫·없이"는 맛 단어 뒤 {@value #AFTER_WINDOW}글자 안에서 시작
 *       ("매운 거 말고", "맵지 않게", "매운 건 빼줘"). 그래서 "30분 안에 매운 거"나 "짭짤한 거 말고 매운 걸로"는 원하는 맛이다</li>
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
    public static List<TasteStance> check(String message, ChatWordProperties.Taste words) {
        String text = ChatTexts.removeWhitespace(message);
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
    public static List<TasteStance> stances(String message, ChatWordProperties.Taste words) {
        String text = ChatTexts.removeWhitespace(message);
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
    public static Set<Member.Taste> mentioned(String message, ChatWordProperties.Taste words) {
        String text = ChatTexts.removeWhitespace(message);
        Set<Member.Taste> tastes = EnumSet.noneOf(Member.Taste.class);
        addIfAny(tastes, text, Member.Taste.SPICY, words.spicy());
        addIfAny(tastes, text, Member.Taste.SWEET, words.sweet());
        addIfAny(tastes, text, Member.Taste.SALTY, words.salty());
        addIfAny(tastes, text, Member.Taste.UMAMI, words.umami());
        addIfAny(tastes, text, Member.Taste.MILD, words.mild());
        return tastes;
    }

    private static void addIfAny(Set<Member.Taste> tastes, String text, Member.Taste taste, List<String> tasteWords) {
        if (tasteWords != null && tasteWords.stream().map(ChatTexts::removeWhitespace)
                .anyMatch(word -> !word.isEmpty() && text.contains(word))) {
            tastes.add(taste);
        }
    }

    // 맛 단어가 하나라도 부정어와 붙어 있으면 피함, 맛 단어만 있으면 원함
    private static Optional<Stance> stance(String text, List<String> tasteWords, ChatWordProperties.Taste words) {
        boolean found = false;
        for (String word : tasteWords) {
            String tasteWord = ChatTexts.removeWhitespace(word);
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
    static boolean negatedBefore(String before, ChatWordProperties.Taste words) {
        return words.negationBefore().stream().map(ChatTexts::removeWhitespace)
                .anyMatch(negation -> !negation.isEmpty() && before.endsWith(negation));
    }

    // 단어 뒤 AFTER_WINDOW글자 안에서 부정어("말고·빼고·않·싫·없이·빼줘·제외·안들어간")가 시작하는지 ("매운게안들어간"처럼 부정어가 창 밖으로 이어져도 본다)
    static boolean negatedAfter(String text, int from, ChatWordProperties.Taste words) {
        return startsWithin(text, from, words.negationAfter()) >= 0;
    }

    /**
     * 메시지가 그 맛을 확실히 원하는지. 부정어가 없고, 맛 단어 바로 앞에 원하는 말("좀 더 매운")이 있거나
     * 뒤 {@value #AFTER_WINDOW}글자 안에서 원하는 말("매운 걸로", "매운 거 먹고 싶어")이 시작할 때만 true.
     * 원하는 말 앞에 "안·덜"이 끼면("매운 거 안 먹고 싶어") 확실하지 않다.
     * 맛 단어만 있고 둘 다 없으면("매운 건 별로") 서버 판단이 애매하므로 false다.
     */
    public static boolean surelyWanted(String message, Member.Taste taste, ChatWordProperties.Taste words) {
        String text = ChatTexts.removeWhitespace(message);
        for (String word : orEmpty(tasteWords(taste, words))) {
            String tasteWord = ChatTexts.removeWhitespace(word);
            if (tasteWord.isEmpty()) {
                continue;
            }
            for (int at = text.indexOf(tasteWord); at >= 0; at = text.indexOf(tasteWord, at + 1)) {
                int end = at + tasteWord.length();
                String before = text.substring(0, at);
                if (negatedBefore(before, words) || negatedAfter(text, end, words)) {
                    continue;
                }
                boolean wantBefore = orEmpty(words.wantBefore()).stream().map(ChatTexts::removeWhitespace)
                        .anyMatch(marker -> !marker.isEmpty() && before.endsWith(marker));
                int marker = startsWithin(text, end, orEmpty(words.wantAfter()));
                boolean wantAfter = marker >= 0 && words.negationBefore().stream().map(ChatTexts::removeWhitespace)
                        .noneMatch(negation -> !negation.isEmpty() && text.substring(end, end + marker).contains(negation));
                if (wantBefore || wantAfter) {
                    return true;
                }
            }
        }
        return false;
    }

    // 맛 조건을 지우라는 말("맛 상관없이", "맛은 아무거나")이 있는지 (공백 빼고 비교)
    public static boolean clearsTastes(String message, ChatWordProperties.Taste words) {
        String text = ChatTexts.removeWhitespace(message);
        return orEmpty(words.clear()).stream().map(ChatTexts::removeWhitespace)
                .anyMatch(word -> !word.isEmpty() && text.contains(word));
    }

    private static List<String> tasteWords(Member.Taste taste, ChatWordProperties.Taste words) {
        return switch (taste) {
            case SPICY -> words.spicy();
            case SWEET -> words.sweet();
            case SALTY -> words.salty();
            case UMAMI -> words.umami();
            case MILD -> words.mild();
        };
    }

    // from 뒤 AFTER_WINDOW글자 안에서 말 하나가 시작하면 그 시작 위치(from 기준), 없으면 -1
    private static int startsWithin(String text, int from, List<String> targets) {
        int limit = Math.min(text.length(), from + AFTER_WINDOW);
        for (int i = from; i < limit; i++) {
            for (String target : targets) {
                String compactTarget = ChatTexts.removeWhitespace(target);
                if (!compactTarget.isEmpty() && text.startsWith(compactTarget, i)) {
                    return i - from;
                }
            }
        }
        return -1;
    }
}
