package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// OpenAI 키 없이 돌릴 때 쓰는 가짜 의도 판단. 항상 일반 질문(GENERAL)으로 본다
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "stub", matchIfMissing = true)
public class StubChatIntentExtractor implements ChatIntentExtractor {

    @Override
    public IntentResult extract(Long memberId, Long sessionId, String message, List<String> categories, String followUp) {
        return IntentResult.general();
    }
}
