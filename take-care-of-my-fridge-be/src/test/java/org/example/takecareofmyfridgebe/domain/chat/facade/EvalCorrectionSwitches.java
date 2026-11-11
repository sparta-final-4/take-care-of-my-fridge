package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 평가(ChatIntentEvalManualTest)에서 끌 보정. 이름은 application.yml app.chat.corrections의 설정 이름과 같다.
 * 예) "taste-words,spoken-tastes-only" → 맛 보정 두 개만 끄고 나머지는 켬. 모르는 이름이 있으면 오타로 보고 멈춘다.
 */
final class EvalCorrectionSwitches {

    static final List<String> NAMES = List.of("taste-words", "spoken-tastes-only", "keep-other-tastes", "refine-spoken-tastes",
            "category-words", "dish-check", "name-only", "mealkit-request", "off-topic-revive", "fridge-shop-words");

    private EvalCorrectionSwitches() {
    }

    // 쉼표로 나눈 끌 설정 이름. 비었으면 모두 켜짐
    static Set<String> parse(String off) {
        Set<String> names = new LinkedHashSet<>();
        if (off == null || off.isBlank()) {
            return names;
        }
        for (String name : off.split(",")) {
            String trimmed = name.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!NAMES.contains(trimmed)) {
                throw new IllegalArgumentException("모르는 보정 이름: " + trimmed + " (쓸 수 있는 이름: " + String.join(", ", NAMES) + ")");
            }
            names.add(trimmed);
        }
        return names;
    }

    static ChatCorrectionProperties properties(Set<String> off) {
        return new ChatCorrectionProperties(
                !off.contains("taste-words"),
                !off.contains("spoken-tastes-only"),
                !off.contains("keep-other-tastes"),
                !off.contains("refine-spoken-tastes"),
                !off.contains("category-words"),
                !off.contains("dish-check"),
                !off.contains("name-only"),
                !off.contains("mealkit-request"),
                !off.contains("off-topic-revive"),
                !off.contains("fridge-shop-words"));
    }

    // 결과 요약 맨 위에 찍을 한 줄
    static String describe(Set<String> off) {
        return off.isEmpty() ? "보정: 모두 켜짐" : "보정: 끈 설정 " + String.join(", ", off);
    }
}
