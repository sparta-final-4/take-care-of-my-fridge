package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyWordProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 메시지에서 알레르기·못 먹는 재료로 말한 낱말을 찾는다. 스프링에 의존하지 않는 순수 클래스.
 * <ul>
 *   <li>"○○ 알레르기", "○○ 못 먹어", "○○ 먹으면 안 돼"처럼 표시 말(markers) 바로 앞 낱말을 ○○로 본다. 표시 말의 글자 사이 공백은 무시한다</li>
 *   <li>낱말 그대로("오이")와 끝 조사를 뗀 것("새우를" → "새우")을 차례로 후보로 낸다. 어느 후보가 재료인지는 부르는 쪽이 정한다
 *       ("매운 거 못 먹어"의 "거"는 재료가 아니라 버려진다)</li>
 *   <li>"약불로 끓여"처럼 표시 말이 없으면 아무것도 찾지 않는다</li>
 * </ul>
 */
public final class AllergyWordChecker {

    private AllergyWordChecker() {
    }

    /**
     * @return 표시 말마다 ○○ 후보 (낱말 그대로, 조사를 뗀 것 순). 없으면 빈 목록
     */
    public static List<List<String>> mentions(String message, ChatAllergyWordProperties words) {
        if (message == null || message.isBlank() || words.markers().isEmpty()) {
            return List.of();
        }
        Matcher matcher = pattern(words.markers()).matcher(message);
        List<String> particles = words.particles().stream()
                .filter(particle -> particle != null && !particle.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        List<List<String>> result = new ArrayList<>();
        while (matcher.find()) {
            String word = matcher.group(1);
            List<String> candidates = new ArrayList<>();
            candidates.add(word);
            for (String particle : particles) {
                if (word.length() > particle.length() && word.endsWith(particle)) {
                    candidates.add(word.substring(0, word.length() - particle.length()));
                }
            }
            result.add(candidates.stream().distinct().toList());
        }
        return result;
    }

    // (낱말) + 공백 + (표시 말 중 하나). 표시 말은 글자 사이에 공백이 있어도 맞는다 ("못 먹어", "먹으면 안 돼")
    private static Pattern pattern(List<String> markers) {
        String alternatives = markers.stream()
                .map(marker -> marker.replaceAll("\\s+", ""))
                .filter(marker -> !marker.isEmpty())
                .map(marker -> marker.chars()
                        .mapToObj(c -> Pattern.quote(String.valueOf((char) c)))
                        .collect(Collectors.joining("\\s*")))
                .collect(Collectors.joining("|"));
        return Pattern.compile("([가-힣A-Za-z]+)\\s*(?:" + alternatives + ")");
    }
}
