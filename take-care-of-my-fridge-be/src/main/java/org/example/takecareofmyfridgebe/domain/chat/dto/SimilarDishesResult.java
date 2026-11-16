package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.List;

/**
 * 비슷한 요리 AI 응답 (prompts/chat-similar-dishes.txt). OpenAI strict json_schema는 맨 위가 객체여야 해서 목록을 칸 하나로 감싼다.
 *
 * @param dishes 받은 요리와 비슷한 요리 이름 (프롬프트는 3~5개를 달라고 한다). 정리는 SimilarDishFinder가 한다
 */
public record SimilarDishesResult(List<String> dishes) {
}
