package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;

import java.util.List;

public interface ChatIntentExtractor {

    /**
     * 상담 메시지에서 의도와 추천 조건을 뽑는다. 값 정리(IntentResult.sanitize)는 부르는 쪽이 한다.
     * 어떤 구현을 쓸지는 상담 답 생성기와 같은 설정(app.chat.reply-generator: stub / openai)으로 고른다.
     *
     * @param memberId   회원 ID (로그용)
     * @param sessionId  세션 ID (로그용)
     * @param message    사용자 메시지
     * @param categories 고를 수 있는 레시피 분류
     * @return 뽑은 의도·조건 (정리 전)
     * @throws RuntimeException AI 호출 실패, 시간 초과, 응답 JSON이 깨진 경우
     */
    IntentResult extract(Long memberId, Long sessionId, String message, List<String> categories);
}
