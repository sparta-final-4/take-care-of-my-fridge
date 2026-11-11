package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatContext.ContextMessage;
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
    public Flux<String> generate(Long memberId, Long sessionId, String userMessage, List<ContextMessage> history) {
        String historyNote = history.isEmpty() ? "" : "이전 대화 " + history.size() + "개, ";
        return chunked("아직 AI 연결 전이에요. " + historyNote + "받은 메시지: " + userMessage);
    }

    // 추천 후보 정보를 그대로 보여 준다 (카드 이벤트와 같이 흐름만 확인)
    @Override
    public Flux<String> generateRecommendation(Long memberId, Long sessionId, String userMessage, String candidateInfo) {
        return chunked("아직 AI 연결 전이에요. 추천 후보: " + candidateInfo);
    }

    // 레시피 정보를 그대로 보여 준다 (카드 하나와 같이 흐름만 확인)
    @Override
    public Flux<String> generateRecipeDetail(Long memberId, Long sessionId, String userMessage, String recipeInfo) {
        return chunked("아직 AI 연결 전이에요. 레시피 정보: " + recipeInfo);
    }

    private static Flux<String> chunked(String fullReply) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < fullReply.length(); i += CHUNK_SIZE) {
            chunks.add(fullReply.substring(i, Math.min(i + CHUNK_SIZE, fullReply.length())));
        }
        return Flux.fromIterable(chunks)
                .delayElements(CHUNK_DELAY);
    }
}
