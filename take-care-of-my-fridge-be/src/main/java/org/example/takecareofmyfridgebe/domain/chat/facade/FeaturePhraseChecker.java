package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatFeatureWordProperties;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * 표현이 요리 이름이 아니라 특징인지 가른다. 스프링에 의존하지 않는 순수 클래스.
 * <p>
 * 낱말이 모두 특징 낱말이거나 일반 말이고, 특징 낱말이 하나 이상이면 특징이다.
 * 특징 낱말은 맛·온도 단어("시원", "매운")로 시작하고 나머지가 없거나 끝말("한", "하고")뿐인 낱말이다.
 * <ul>
 *   <li>"시원한 국물", "매운 요리", "따뜻한 거" → 특징</li>
 *   <li>"매운탕"(나머지 "탕"), "김치찌개", "시원한 냉면"(냉면은 일반 말이 아님), "국물"(특징 낱말 없음) → 특징 아님</li>
 * </ul>
 * AI가 "시원한 국물 요리"의 특징을 키워드에 그대로 넣으면 서버가 요리 이름으로 채워 "시원한 국물 레시피는 없어서…"라고 안내하던 것을 막는다.
 * 비교는 공백을 뺀 낱말 단위로 한다.
 */
public final class FeaturePhraseChecker {

    private FeaturePhraseChecker() {
    }

    /**
     * @param featureStems 맛·온도 단어 ("시원", "매운", "따뜻")
     */
    public static boolean isFeature(String phrase, Collection<String> featureStems, ChatFeatureWordProperties words) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }
        List<String> tokens = List.of(phrase.strip().split("\\s+"));
        boolean hasFeature = false;
        for (String token : tokens) {
            if (featureToken(token, featureStems, words)) {
                hasFeature = true;
            } else if (!words.generic().contains(token)) {
                return false;
            }
        }
        return hasFeature;
    }

    // 맛·온도 단어로 시작하고 나머지가 없거나 끝말뿐인 낱말 ("시원한", "매운"). "매운탕"은 아니다
    private static boolean featureToken(String token, Collection<String> featureStems, ChatFeatureWordProperties words) {
        return featureStems.stream()
                .filter(stem -> !stem.isBlank() && token.startsWith(stem))
                .map(stem -> token.substring(stem.length()))
                .anyMatch(rest -> rest.isEmpty() || words.endings().contains(rest));
    }

    // 맛 단어 목록들과 온도 단어 목록들을 하나로 모은다
    @SafeVarargs
    public static List<String> stems(List<String>... lists) {
        return Stream.of(lists).flatMap(List::stream).distinct().toList();
    }
}
