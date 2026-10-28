package org.example.takecareofmyfridgebe.domain.chat.service;

import org.springframework.stereotype.Component;

@Component
public class StubChatReplyGenerator implements ChatReplyGenerator {

    @Override
    public String generate(Long sessionId, String userMessage) {
        return "아직 AI 연결 전이에요. 받은 메시지: " + userMessage;
    }
}
