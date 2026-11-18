package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.JevIntentJudgement;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneCall;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneQuestion;
import org.example.takecareofmyfridgebe.infra.typesafe.dto.SystemOneResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jev 답 읽기. 질문 이름의 답이 없거나, 모르는 선택지가 오거나, 칸이 비면 JevAnswerException을 던진다
 * (JevIntentClassifier가 받아 실패로 처리한다). 예외 글에는 질문 이름·선택지만 쓰고 사용자 글은 쓰지 않는다.
 */
final class JevAnswers {

    private final SystemOneCall call;
    private final Map<String, SystemOneResponse.Answer> answers;

    JevAnswers(SystemOneCall call) {
        this.call = call;
        Map<String, SystemOneResponse.Answer> received = call.response().answers();
        this.answers = received == null ? Map.of() : received;
    }

    /**
     * Choice 질문의 선택지 하나.
     *
     * @param label       보내고 받는 선택지 이름 (한국어)
     * @param value       그 선택지가 뜻하는 값
     * @param description 선택지 설명
     */
    record Option<E>(String label, E value, String description) {
    }

    record Choice<E>(E value, double confidence, Map<E, Double> probabilities) {
    }

    static <E> SystemOneQuestion choiceQuestion(String instructions, List<Option<E>> options) {
        Map<String, String> criteria = new LinkedHashMap<>();
        options.forEach(option -> criteria.put(option.label(), option.description()));
        return SystemOneQuestion.choice(instructions, criteria);
    }

    // 확률은 선택지 순서로 담고, 응답에 없는 선택지는 0으로 본다
    <E> Choice<E> choice(String name, List<Option<E>> options) {
        SystemOneResponse.Answer answer = require(name);
        if (answer.choice() == null || answer.confidence() == null || answer.probabilities() == null) {
            throw new JevAnswerException(name + " 답에 choice·confidence·probabilities가 없음");
        }
        E value = options.stream()
                .filter(option -> option.label().equals(answer.choice()))
                .map(Option::value)
                .findFirst()
                .orElseThrow(() -> new JevAnswerException(name + " 답이 모르는 선택지: " + answer.choice()));
        Map<E, Double> probabilities = new LinkedHashMap<>();
        options.forEach(option -> probabilities.put(option.value(), answer.probabilities().getOrDefault(option.label(), 0.0)));
        return new Choice<>(value, answer.confidence(), Collections.unmodifiableMap(probabilities));
    }

    double noul(String name) {
        Double noul = require(name).noul();
        if (noul == null) {
            throw new JevAnswerException(name + " 답에 noul이 없음");
        }
        return noul;
    }

    JevIntentJudgement.Call call() {
        return new JevIntentJudgement.Call(call.response().model(), call.requestId(), call.latencyMs(), call.inputTokens());
    }

    private SystemOneResponse.Answer require(String name) {
        SystemOneResponse.Answer answer = answers.get(name);
        if (answer == null) {
            throw new JevAnswerException(name + " 답이 없음");
        }
        return answer;
    }

    static class JevAnswerException extends RuntimeException {

        JevAnswerException(String message) {
            super(message);
        }
    }
}
