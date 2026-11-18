package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.IntentExtraction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// OpenAI 키 없이 돌릴 때 쓰는 가짜 꺼내기. 아무것도 꺼내지 않는다
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "stub", matchIfMissing = true)
public class StubChatIntentExtractor implements ChatIntentExtractor {

    @Override
    public IntentExtraction extract(Long memberId, Long sessionId, String message, String followUp) {
        return IntentExtraction.empty();
    }
}
