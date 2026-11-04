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
    @DisplayName("임박 표시는 오늘까지·내일까지·모레까지·3일 남음을 그대로 말하라고 지시한다")
    void instructsDdayInWords() {
        assertThat(prompt).contains("(오늘까지)", "(내일까지)", "(모레까지)", "(3일 남음)", "괄호 표시를 다른 날짜로 바꿔 말하지 않는다");
    }

    @Test
    @DisplayName("카드의 후보를 하나도 빼지 않고 모두 소개하고, 조리 시간이 없으면 그렇다고만 말하라고 지시한다")
    void instructsToIntroduceAllCards() {
        assertThat(prompt).contains("후보를 하나도 빼지 않고 모두 번호 순서대로 소개한다", "조리 시간 정보가 없어요");
    }

    @Test
    @DisplayName("마크다운을 쓰지 말고 번호와 줄바꿈으로만 나누라고 지시한다")
    void forbidsMarkdown() {
        assertThat(prompt).contains("마크다운을 쓰지 않는다", "번호와 줄바꿈으로만");
    }

    @Test
    @DisplayName("선택 재료를 어떻게 쓰는지 먼저 말하고, 내부 조건·판단 과정은 말하지 않으라고 지시한다")
    void instructsSelectedFirstAndNoInternalReasoning() {
        assertThat(prompt).contains("그 선택 재료를 어떻게 쓰는지", "내부 조건이나 판단 과정은 말하지 않는다");
    }
}
