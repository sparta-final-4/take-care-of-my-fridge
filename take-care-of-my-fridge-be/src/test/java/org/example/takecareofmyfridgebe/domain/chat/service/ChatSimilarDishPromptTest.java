package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 비슷한 요리 묻기 프롬프트(prompts/chat-similar-dishes.txt)에 꼭 있어야 하는 지시
class ChatSimilarDishPromptTest {

    @Test
    @DisplayName("비슷한 요리 3~5개만 내되 모르는 요리는 빈 목록으로, 오타는 고친 이름을 맨 앞에 쓰고 설명하는 말은 빼라고 지시하며 평가 대상은 예시에 없다")
    void instructsSimilarDishesOnly() throws IOException {
        String prompt = new ClassPathResource("prompts/chat-similar-dishes.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).contains("- dishes: 받은 요리와 비슷한 요리 이름 3~5개",
                "어떤 요리인지 확실히 모르면 dishes를 빈 목록으로 낸다",
                "이름 글자만 보고 짐작하지 않는다",
                "받은 요리 이름 자신, 분류 이름",
                "흔한 요리 이름의 오타면 바르게 고친 이름을 맨 앞에 쓴다",
                "요리를 설명하는 말");
        assertThat(prompt).doesNotContain("처음 들어도", "김피탕", "나가사키", "꿔바로우", "마라샹궈", "김치찌게",
                "avoidTastes", "maxMinutes");
    }
}
