package org.example.takecareofmyfridgebe.domain.chat.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// OpenAI 키 없이 상담 흐름을 확인하는 가짜 답변기. app.chat.reply-generator가 없거나 stub이면 쓴다
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "stub", matchIfMissing = true)
public class StubChatReplyGenerator implements ChatReplyGenerator {

    private static final int CHUNK_SIZE = 3;
    private static final Duration CHUNK_DELAY = Duration.ofMillis(50);

    @Override
    public Flux<String> generate(Long memberId, Long sessionId, String userMessage) {
        String fullReply = "아직 AI 연결 전이에요. 받은 메시지: " + userMessage;
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < fullReply.length(); i += CHUNK_SIZE) {
            chunks.add(fullReply.substring(i, Math.min(i + CHUNK_SIZE, fullReply.length())));
        }
        return Flux.fromIterable(chunks)
                .delayElements(CHUNK_DELAY);
    }
}
