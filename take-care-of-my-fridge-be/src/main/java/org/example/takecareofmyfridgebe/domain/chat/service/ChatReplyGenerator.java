package org.example.takecareofmyfridgebe.domain.chat.service;

import reactor.core.publisher.Flux;

public interface ChatReplyGenerator {

    /**
     * 사용자 메시지를 바탕으로 상담 답변을 스트리밍(Flux) 형태로 생성한다.
     * 어떤 구현을 쓸지는 app.chat.reply-generator(stub / openai)로 고른다.
     *
     * @param memberId    회원 ID (로그용)
     * @param sessionId   세션 ID
     * @param userMessage 사용자 입력 메시지
     * @return AI 상담 답변 문자열 스트림
     */
    Flux<String> generate(Long memberId, Long sessionId, String userMessage);
}
