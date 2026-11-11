package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatWordProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 메시지에 레시피 분류 이름("디저트", "국")이 그대로 있는지 서버가 한 번 더 본다. 스프링에 의존하지 않는 순수 클래스.
 * 후속 질문에서 AI가 앞 대화에 끌려 분류를 비우거나 다른 분류를 넣어도, 이번 말에 있는 분류를 쓰게 한다.
 * <p>
 * 분류 이름이 다른 말 안에 들어가는 경우("국수", "밥솥", "전자레인지", "김치찌개", "김치로 만든")를 피하려고 단어 단위로 본다.
 * <ol>
 *   <li>공백으로 나눈 단어가 분류 이름으로 시작하고, 남은 글자가 없거나 조사({@link #SUFFIXES})여야 한다.
 *       "로·으로"는 재료로 쓰는 말("김치로", "떡으로")과 겹쳐 넣지 않는다</li>
 *   <li>다음 단어가 요청하는 말({@link #REQUEST_WORDS}로 시작: 추천·먹·만들·말고 …)이거나 메시지 끝이어야 한다.
 *       "김치 넣은 요리", "떡 썰어서"처럼 재료로 말한 것을 뺀다</li>
 *   <li>분류 이름과 다른 흔한 말("국물" → 국·찌개·전골·수프·스튜, 설정 app.chat.words.category)도 같은 규칙으로 찾고, 맞으면 묶음의 분류를 모두 센다.
 *       "국수"는 묶음 말이 아니어서 국으로 보지 않는다.
 *       {@value #MIN_PHRASE_LENGTH}글자 이상인 묶음 말(설정에 붙여 쓴 "간단한한끼")은 띄어 쓴 메시지("간단한 한 끼 추천해줘")에서도 찾는다</li>
 *   <li>대명사·끼니와 겹치는 분류({@link #SKIPPED}: "전 매운 거 좋아해"의 전, "밥 뭐 먹지"의 밥)는 서버가 채우지 않고 AI에 맡긴다</li>
 *   <li>부정어는 맛 단어와 같은 기준이다(TasteWordChecker): 분류 이름 바로 앞 "안·덜", 뒤 4글자 안 "말고·빼고·않·싫·없이"면 피함
 *       ("디저트 말고 국" → 디저트 피함, 국 원함)</li>
 * </ol>
 */
public final class CategoryWordChecker {

    // 분류 이름 바로 뒤에 붙어도 그 분류로 보는 말
    static final Set<String> SUFFIXES = Set.of(
            "", "은", "는", "이", "가", "을", "를", "도", "만", "랑", "이랑", "하고", "나", "이나", "요리", "류", "종류", "메뉴");
    // 분류 이름 다음 단어가 이 말로 시작하면 요청으로 본다. "끓"은 국·찌개를 끓이는 말("찌개 끓이고 싶어", "국 끓여 먹을")
    static final List<String> REQUEST_WORDS = List.of(
            "추천", "먹", "만들", "해", "할", "요리", "메뉴", "종류", "같은", "같이", "중", "뭐", "좀", "하나", "어때", "없", "있",
            "말고", "빼고", "싫", "위주", "줘", "알려", "골라", "땡", "당기", "원해", "좋아", "찾아", "밀키트", "끓");
    // 대명사(전 = 저는)·끼니(밥 = 식사)로 흔히 쓰여 서버가 채우지 않는 분류
    static final Set<String> SKIPPED = Set.of("전", "밥");
    // 이 글자 수 이상인 묶음 말은 여러 낱말로 띄어 써도 찾는다 ("간단한 한 끼" ↔ 간단한한끼). 짧은 말("면", "국물")은 "라면"처럼 다른 말 안에 들어가 낱말 단위로만 본다
    static final int MIN_PHRASE_LENGTH = 4;

    private static final String LEADING_MARKS = "\"'(“‘[";
    private static final String TRAILING_MARKS = "?!.,~…\"')”’]";

    private CategoryWordChecker() {
    }

    public enum Stance { WANT, AVOID }

    public record CategoryStance(String category, Stance stance) {
    }

    /**
     * @param categories 고를 수 있는 분류 (AI에 준 분류 목록과 같은 것, "기타" 제외)
     * @return 메시지에 나온 순서대로 분류마다 원하는지·피하는지. 같은 분류가 한 번이라도 부정어와 붙으면 피함
     */
    public static List<CategoryStance> check(String message, Collection<String> categories, ChatWordProperties.Taste words) {
        return check(message, categories, words, List.of());
    }

    /**
     * @param groups 분류 이름과 다른 묶음 말 ("국물" → 국·찌개·전골·수프·스튜). 분류 이름과 같은 규칙으로 찾고, 맞으면 묶음의 분류 모두를 센다.
     *               categories에 없는 분류는 뺀다
     */
    public static List<CategoryStance> check(String message, Collection<String> categories, ChatWordProperties.Taste words,
                                             List<ChatWordProperties.Category.Group> groups) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        // 말 → 분류. 긴 말부터 본다 ("전골"을 "전"보다, "국물"을 "국"보다 먼저)
        Map<String, List<String>> terms = new LinkedHashMap<>();
        categories.stream()
                .filter(name -> name != null && !name.isBlank() && !SKIPPED.contains(name))
                .forEach(name -> terms.put(name, List.of(name)));
        for (ChatWordProperties.Category.Group group : groups) {
            List<String> mapped = group.categories().stream().filter(categories::contains).toList();
            if (group.word() != null && !group.word().isBlank() && !mapped.isEmpty()) {
                terms.putIfAbsent(group.word().strip(), mapped);
            }
        }
        List<String> names = terms.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        String compact = message.replaceAll("\\s+", "");
        String[] tokens = message.strip().split("\\s+");

        Map<String, Stance> stances = new LinkedHashMap<>();
        int offset = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int lead = leadingMarks(token);
            String word = stripTrailing(token.substring(lead));
            int start = offset + lead;
            offset += token.length();
            String next = i + 1 < tokens.length ? stripTrailing(tokens[i + 1].substring(leadingMarks(tokens[i + 1]))) : null;

            for (String name : names) {
                if (!word.startsWith(name)) {
                    continue;
                }
                String rest = word.substring(name.length());
                boolean negatedInWord = startsWithAny(rest, words.negationAfter());
                if (!negatedInWord && (!SUFFIXES.contains(rest) || (next != null && !startsWithAny(next, REQUEST_WORDS)))) {
                    continue;
                }
                boolean avoid = negatedInWord
                        || TasteWordChecker.negatedBefore(compact.substring(0, start), words)
                        || TasteWordChecker.negatedAfter(compact, start + name.length(), words);
                Stance stance = avoid ? Stance.AVOID : Stance.WANT;
                for (String category : terms.get(name)) {
                    stances.merge(category, stance,
                            (before, now) -> before == Stance.AVOID || now == Stance.AVOID ? Stance.AVOID : Stance.WANT);
                }
                // 한 단어에서는 가장 긴 분류 이름 하나만 센다
                break;
            }
        }
        // 여러 낱말로 된 묶음 말("간단한한끼")은 낱말 단위로는 걸리지 않으므로 공백을 뺀 글에서 한 번 더 찾는다
        for (ChatWordProperties.Category.Group group : groups) {
            String phrase = group.word() == null ? "" : group.word().replaceAll("\\s+", "");
            int start = phrase.length() >= MIN_PHRASE_LENGTH ? compact.indexOf(phrase) : -1;
            List<String> mapped = terms.get(group.word() == null ? "" : group.word().strip());
            if (start < 0 || mapped == null) {
                continue;
            }
            boolean avoid = TasteWordChecker.negatedBefore(compact.substring(0, start), words)
                    || TasteWordChecker.negatedAfter(compact, start + phrase.length(), words);
            Stance stance = avoid ? Stance.AVOID : Stance.WANT;
            for (String category : mapped) {
                stances.merge(category, stance,
                        (before, now) -> before == Stance.AVOID || now == Stance.AVOID ? Stance.AVOID : Stance.WANT);
            }
        }
        List<CategoryStance> result = new ArrayList<>();
        stances.forEach((name, stance) -> result.add(new CategoryStance(name, stance)));
        return result;
    }

    private static int leadingMarks(String token) {
        int i = 0;
        while (i < token.length() && LEADING_MARKS.indexOf(token.charAt(i)) >= 0) {
            i++;
        }
        return i;
    }

    private static String stripTrailing(String word) {
        int end = word.length();
        while (end > 0 && TRAILING_MARKS.indexOf(word.charAt(end - 1)) >= 0) {
            end--;
        }
        return word.substring(0, end);
    }

    private static boolean startsWithAny(String text, Collection<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> !prefix.isEmpty() && text.startsWith(prefix));
    }
}
