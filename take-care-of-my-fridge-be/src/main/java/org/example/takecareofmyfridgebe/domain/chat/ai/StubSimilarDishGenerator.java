package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// OpenAI 키 없이 돌릴 때 쓰는 가짜 비슷한 요리. 늘 빈 목록 (비슷한 요리 없이 이름 조각·벡터 보충으로 찾는다)
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "stub", matchIfMissing = true)
public class StubSimilarDishGenerator implements SimilarDishGenerator {

    @Override
    public List<String> generate(Long memberId, String dishText) {
        return List.of();
    }
}
