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

    @Test
    @DisplayName("추천 기준 설명에 맞는 레시피가 없을 때 냉장고 재료로 3장을 고르고 알리는 것과 온도 말 거르기가 실제 고르기대로 있다")
    void describesPickingAsServerDoes() throws IOException {
        String prompt = new ClassPathResource("prompts/chat-general.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).contains("말씀하신 맛이나 온도와 반대가 아닌 레시피로만 채우고",
                "맞는 레시피가 하나도 없으면 냉장고 재료로 만들 수 있는 요리 세 개를 고르고, 맞는 레시피가 없었다고 먼저 알려 드려요",
                "따뜻한 요리를 말씀하시면 냉면·냉국 같은 차가운 요리는 빼고, 시원한 요리를 말씀하시면 뜨거운 국물 요리는 뒤로 보내요");
    }
}
