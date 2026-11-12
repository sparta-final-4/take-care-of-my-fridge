package org.example.takecareofmyfridgebe.domain.chat.intent;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext;

import java.util.List;

/**
 * 의도 보정(IntentCorrector.correct) 한 번 동안 바뀌지 않는 입력.
 * 보정 결과(IntentResult)는 단계마다 바뀌므로 넣지 않고 따로 넘긴다.
 *
 * @param message    사용자 메시지 원문
 * @param categories AI에 준 분류 목록
 * @param context    직전 추천·카드 등 대화 문맥
 */
public record CorrectionInput(
        Long memberId,
        Long sessionId,
        String message,
        List<String> categories,
        ChatContext context
) {
}
