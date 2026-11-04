package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 레시피 자세히 답 프롬프트(prompts/chat-recipe-detail.txt)에 꼭 있어야 하는 지시
class ChatRecipeDetailPromptTest {

    private static String prompt;

    @BeforeAll
    static void readPrompt() throws IOException {
        prompt = new ClassPathResource("prompts/chat-recipe-detail.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("[레시피 정보]에 있는 것만 말하고, 있으면 바로 답하며, \"나와 있지 않아요\"는 정말 없을 때만 무엇이 없는지 말하게 한다")
    void forbidsMakingThingsUp() {
        assertThat(prompt).contains("[레시피 정보]에 있는 내용으로만 답한다. 재료 이름·양, 조리 시간, 단계, 팁을 지어내거나 바꾸지 않는다",
                "물은 내용이 [레시피 정보]에 있으면 바로 그 내용으로 답한다. 앞에 \"나와 있지 않아요\" 같은 말을 붙이지 않는다",
                "물은 내용이 [레시피 정보]에 정말 없을 때만",
                "\"조리 시간: 레시피에 없음\"인데 시간을 물으면 \"레시피에는 조리 시간이 나와 있지 않아요.\"",
                "물은 것만 답한다. 시간을 물으면 시간만")
                .doesNotContain("\"레시피에는 나와 있지 않아요\"라고 먼저 말한다");
    }

    @Test
    @DisplayName("단계는 레시피대로 모두 한 줄씩 10줄까지, 생략된 뒤 단계는 지어내지 않고 카드를 보라고 하며, 냉장고 기준은 물었을 때만 말하게 한다")
    void instructsStepsAndFridge() {
        assertThat(prompt).contains("단계는 레시피대로 모두 \"1. …\"처럼 한 줄씩, 10줄까지 쓴다. 단계를 빼거나 합치지 않는다",
                "\"(뒤 n단계 생략)\"이 있으면 뒤 단계를 지어내지 말고 카드를 눌러 보라고 말한다",
                "[냉장고 기준]은 냉장고에 있는지, 장볼 게 있는지, 만들 수 있는지 물었을 때만 말한다");
    }
}
