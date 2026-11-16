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
    @DisplayName("받은 요리와 비슷한 요리 이름 3~5개만 내고, 자기 이름·분류 이름은 빼며, 처음 듣는 이름도 짐작하라고 지시하고 조건 칸은 없다")
    void instructsSimilarDishesOnly() throws IOException {
        String prompt = new ClassPathResource("prompts/chat-similar-dishes.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).contains("- dishes: 받은 요리와 비슷한 요리 이름 3~5개",
                "받은 요리를 처음 들어도 이름에서 짐작해 비슷한 요리를 고른다",
                "받은 요리 이름 자신, 분류 이름");
        assertThat(prompt).doesNotContain("김피탕", "나가사키", "avoidTastes", "maxMinutes");
    }
}
