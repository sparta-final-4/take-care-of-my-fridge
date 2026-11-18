package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.List;

/**
 * 넓은 요청 요리 AI 응답 (prompts/chat-broad-dishes.txt). OpenAI strict json_schema는 맨 위가 객체여야 해서 목록을 칸 하나로 감싼다.
 *
 * @param dishes 요청에 맞는 요리 이름. 정리는 SimilarDishFinder가 한다
 */
public record BroadDishesResult(List<String> dishes) {
}
