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
    @DisplayName("소개 한두 문장만 쓰고 레시피별 설명·번호 목록·카드 안내 마무리는 쓰지 말라고 지시한다")
    void instructsIntroOnly() {
        assertThat(prompt).contains("소개 한두 문장만 쓴다", "레시피별 설명, 번호 목록, \"카드를 눌러…\" 같은 마무리는 쓰지 않는다",
                "사용자 말에 맞춰 후보 전체를 소개한다");
    }

    @Test
    @DisplayName("[먼저 말할 것]이 있으면 그 문장으로 시작하고, 없으면 냉장고에 없는 재료 이야기를 하지 않는다")
    void instructsNoticeFirst() {
        assertThat(prompt).contains("[먼저 말할 것]이 있으면 그 문장으로 시작하고",
                "[먼저 말할 것]이 없으면 냉장고에 없는 재료 이야기를 하지 않는다").doesNotContain("말씀하신 버섯");
    }

    @Test
    @DisplayName("임박 재료는 한 번 짧게, 소비기한 말은 바꾸지 말고, 선택 재료는 주재료로 먼저 말하라고 지시한다")
    void keepsImminentAndSelectedRules() {
        assertThat(prompt).contains("내일까지인 고추", "적힌 그대로 쓰고 바꾸지 않는다", "쓰는 냉장고 재료를 전부 나열하지 않는다",
                "선택 재료가 주재료로 들어간다는 말을 먼저 한다", "넣어도 되고 안 넣어도 되는 재료처럼 말하지 않는다");
    }

    @Test
    @DisplayName("부족한 재료 대신 장볼 재료로 말하고, 카드에 있는 정보는 반복하지 말라고 지시한다")
    void instructsShoppingWordingAndNoRepeat() {
        assertThat(prompt).contains("\"부족한 재료\"라고 하지 않고 \"장볼 재료\"로 말한다", "장볼 재료 목록을 나열하지 않는다",
                "분류·조리 시간·추천 이유를 레시피마다 다시 말하지 않는다");
    }

    @Test
    @DisplayName("내부 조건·판단 과정은 말하지 말고, 마크다운 금지는 공통 프롬프트에 둔다")
    void forbidsInternalReasoning() {
        assertThat(prompt).contains("내부 조건이나 판단 과정은 말하지 않는다").doesNotContain("마크다운");
    }
}
