package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;

public interface ChatIntentExtractor {

    /**
     * 상담 메시지에서 요리 이름·키워드·재료·시간 등 꺼낼 값을 꺼낸다. 의도는 정하지 않는다 (Jev가 정한다).
     * 값 정리(IntentExtraction.sanitize)는 부르는 쪽이 한다.
     * 어떤 구현을 쓸지는 상담 답 생성기와 같은 설정(app.chat.reply-generator: stub / openai)으로 고른다.
     *
     * @param memberId  회원 ID (로그용)
     * @param sessionId 세션 ID (로그용)
     * @param message   사용자 메시지
     * @param followUp  직전 추천 정보 줄 글([직전 추천]·[이전 추천]·[직전 조건]). 직전 추천이 없으면(첫 질문) null.
     *                  있으면 이어지는 말에서 꺼내는 규칙과 함께 고정 시스템 프롬프트 뒤에 별도 메시지로 넣는다
     * @return 꺼낸 값 (정리 전)
     * @throws RuntimeException AI 호출 실패, 시간 초과, 응답 JSON이 깨진 경우
     */
    IntentExtraction extract(Long memberId, Long sessionId, String message, String followUp);
}
