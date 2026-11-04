package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 추천 설명 프롬프트(prompts/chat-recommend.txt)에 꼭 있어야 하는 지시
class ChatRecommendPromptTest {

    private static String prompt;

    @BeforeAll
    static void readPrompt() throws IOException {
        prompt = new ClassPathResource("prompts/chat-recommend.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("소비기한이 붙은 재료만 한 번 짧게 말하고, 소비기한 말을 바꾸지 말고, 쓰는 재료를 전부 나열하지 말라고 지시한다")
    void instructsImminentBriefly() {
        assertThat(prompt).contains("내일까지인 고추", "그 재료만 레시피마다 한 번 짧게 말한다", "적힌 그대로 쓰고 바꾸지 않는다",
                "쓰는 냉장고 재료를 전부 나열하지 않는다");
    }

    @Test
    @DisplayName("선택 재료 쓰임이 있으면 레시피마다 첫 문장을 주재료로 들어간다는 말로 시작하라고 지시한다")
    void instructsSelectedAsMainIngredient() {
        assertThat(prompt).contains("선택 재료 쓰임", "첫 문장을 그 재료가 요리의 주재료로 들어간다는 말로 시작한다",
                "넣어도 되고 안 넣어도 되는 재료처럼 말하지 않는다");
    }

    @Test
    @DisplayName("카드의 후보를 하나도 빼지 않고 모두 소개하고, 조리 시간이 없으면 그렇다고만 말하라고 지시한다")
    void instructsToIntroduceAllCards() {
        assertThat(prompt).contains("후보를 하나도 빼지 않고 모두 번호 순서대로 소개한다", "조리 시간 정보가 없어요");
    }

    @Test
    @DisplayName("마크다운 금지는 공통 프롬프트에 두고 추천 프롬프트에는 다시 쓰지 않는다")
    void leavesMarkdownRuleToSystemPrompt() {
        assertThat(prompt).doesNotContain("마크다운").contains("번호와 줄바꿈으로 나눈다");
    }

    @Test
    @DisplayName("내부 조건·판단 과정은 말하지 않으라고 지시한다")
    void forbidsInternalReasoning() {
        assertThat(prompt).contains("내부 조건이나 판단 과정은 말하지 않는다");
    }

    @Test
    @DisplayName("[먼저 말할 것]이 있을 때만 그 문장을 맨 앞에 쓰고, 예시 재료 이름은 넣지 않는다")
    void instructsNoticeOnlyWhenGiven() {
        assertThat(prompt).contains("[먼저 말할 것]이 있으면 그 문장을", "[먼저 말할 것]이 없으면 냉장고에 없는 재료 이야기를 하지 않는다")
                .doesNotContain("말씀하신 버섯");
    }
}
