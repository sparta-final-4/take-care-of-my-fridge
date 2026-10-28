package org.example.takecareofmyfridgebe.domain.chat.service;

public interface ChatReplyGenerator {

    /**
     * 세션 ID와 사용자 메시지를 바탕으로 상담 답변을 생성한다.
     *
     * @param sessionId   세션 ID
     * @param userMessage 사용자 입력 메시지
     * @return AI 상담 답변 문자열
     */
    String generate(Long sessionId, String userMessage);
}
