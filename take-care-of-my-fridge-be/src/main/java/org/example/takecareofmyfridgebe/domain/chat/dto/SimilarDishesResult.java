package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.List;

/**
 * 비슷한 요리 AI 응답 (prompts/chat-similar-dishes.txt). OpenAI strict json_schema는 맨 위가 객체여야 해서 칸으로 감싼다.
 * 정식 이름은 비슷한 요리와 같은 호출에서 받는다 (호출을 늘리지 않는다).
 *
 * @param formalName 받은 요리 이름이 줄임말이면 정식 이름, 아니거나 모르면 빈 문자열
 * @param dishes     받은 요리와 비슷한 요리 이름 (프롬프트는 3~5개를 달라고 한다). 정리는 SimilarDishFinder가 한다
 */
public record SimilarDishesResult(String formalName, List<String> dishes) {
}
