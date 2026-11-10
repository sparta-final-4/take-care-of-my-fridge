package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 일반 답 프롬프트(prompts/chat-general.txt)에 꼭 있어야 하는 지시
class ChatGeneralPromptTest {

    @Test
    @DisplayName("일반 답은 레시피의 재료·조리법을 지어내지 말고 요리를 물으면 추천을 권하라고 지시한다")
    void forbidsMadeUpRecipes() throws IOException {
        String prompt = new ClassPathResource("prompts/chat-general.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).contains("레시피의 재료·조리법을 지어내서 쓰지 않는다", "레시피 추천해 드릴까요?");
    }
}
