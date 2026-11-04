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
    @DisplayName("맨 위에 [소개]·[1]~[3] 표시 형식과 출력 예시를 두고, 다른 머리말·번호 목록·카드 안내 마무리는 쓰지 말라고 한다")
    void putsMarkerFormatFirstWithExample() {
        assertThat(prompt).startsWith("[추천 답 형식] 가장 먼저 지킨다.");
        assertThat(prompt).contains("[소개] 전체를 소개하는 한 문장", "[1] 1번 후보 설명 한두 문장", "[3] 3번 후보 설명 한두 문장",
                "표시 말고 다른 머리말·번호 목록·마무리 인사(\"카드를 눌러…\")는 쓰지 않는다",
                "후보가 2개면 [2]까지", "[소개] 냉면처럼 즐길 수 있는 면 요리를 골랐어요.");
    }

    @Test
    @DisplayName("카드 설명은 왜 골랐는지(카드 이유)를 먼저, 그다음 맛이나 팁을 한두 문장으로 쓰라고 지시한다")
    void instructsCardDescription() {
        assertThat(prompt).contains("먼저 왜 골랐는지를 말한다. 후보 줄의 \"카드 이유\"를 근거로 쓴다",
                "그다음 맛이나 간단한 조리 팁을 한 가지 말한다");
    }

    @Test
    @DisplayName("[먼저 말할 것]이 있으면 [소개]를 그 문장으로 시작하고, 없으면 냉장고에 없는 재료 이야기를 하지 않는다")
    void instructsNoticeFirst() {
        assertThat(prompt).contains("[먼저 말할 것]이 있으면 [소개]를 그 문장으로 시작한다",
                "[먼저 말할 것]이 없으면 냉장고에 없는 재료 이야기를 하지 않는다").doesNotContain("말씀하신 버섯");
    }

    @Test
    @DisplayName("임박은 짧게·말 그대로, 선택 재료는 주재료로, 장볼 재료 표현, 카드 정보에 없는 특징·찾았다는 단정 금지는 그대로다")
    void keepsExistingRules() {
        assertThat(prompt).contains("내일까지인 고추", "적힌 그대로 쓰고 바꾸지 않는다", "쓰는 냉장고 재료를 전부 나열하지 않는다",
                "그 재료가 주재료로 들어간다는 말로 시작한다", "\"부족한 재료\"라고 하지 않고 \"장볼 재료\"로 말한다",
                "카드 정보에 없는 특징(차가운, 따뜻한, 매운, 바삭한 등)을 붙여 단정하지 않는다",
                "사용자 요청을 되풀이해서 \"○○로 추천해 드릴게요\"라고 하지 않는다",
                "\"찾지 못했어요\", \"조건을 바꿔 보세요\"");
    }

    @Test
    @DisplayName("내부 조건·판단 과정은 말하지 말고, 마크다운 금지는 공통 프롬프트에 둔다")
    void forbidsInternalReasoning() {
        assertThat(prompt).contains("내부 조건이나 판단 과정은 말하지 않는다").doesNotContain("마크다운");
    }
}
