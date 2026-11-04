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
    @DisplayName("카드 설명은 요리 특징(맛·식감)과 요청과의 관계나 팁을 쓰고, 카드에 있는 재료·소비기한·장볼 재료·조리 시간은 쓰지 않게 한다")
    void instructsCardDescription() {
        assertThat(prompt).contains("어떤 요리인지(맛·식감)를 말하고, 요청과의 관계(\"김피탕과 비슷한 새콤달콤한 맛\") 또는 간단한 팁",
                "쓰는 냉장고 재료·장볼 재료 이름, 소비기한(\"내일까지인\"), 조리 시간, 분류를 쓰지 않는다",
                "[1] 바삭하게 튀긴 닭에 매콤달콤한 소스를 버무린 중식 요리예요.");
    }

    @Test
    @DisplayName("후보가 3개면 [3]까지 빠짐없이 쓰고, 요청과 덜 맞는 후보도 짧게라도 쓰게 한다")
    void requiresAllCardDescriptions() {
        assertThat(prompt).contains("후보가 3개면 [3]까지 꼭 쓰고, 하나라도 빠뜨리지 않는다",
                "요청과 덜 맞는 후보도 건너뛰지 말고 짧게라도 쓴다");
    }

    @Test
    @DisplayName("[다른 제안] 후보는 요청과 다른 제안이라고 자연스럽게 말하고 표시는 그대로 쓰지 않게 한다")
    void instructsAlternativeCandidate() {
        assertThat(prompt).contains("이름 앞에 [다른 제안]이 붙은 후보",
                "\"색다르게 ○○도 곁들여 보세요\", \"다른 걸로는 ○○도 좋아요\"",
                "\"[다른 제안]\"이라는 표시는 그대로 쓰지 않는다",
                "[3] 색다르게 고소한 찹쌀부침도 곁들여 보세요.");
    }

    @Test
    @DisplayName("소비기한 재료는 소개에서 한 번만 말하게 한다")
    void allowsImminentOnceInIntro() {
        assertThat(prompt).contains("소개에서 \"내일까지인 고추를 같이 쓸 수 있어요\"처럼 한 번만 말해도 된다");
    }

    @Test
    @DisplayName("안내 문장은 서버가 이미 보냈으니 [이미 안내한 말]을 반복하지 말고, 답은 [소개]로 시작하며 다른 대괄호 표시는 쓰지 않게 한다")
    void instructsNotRepeatingNotice() {
        assertThat(prompt).contains("답은 반드시 [소개]로 시작한다. [소개] 앞에 아무 글도 쓰지 않는다",
                "[이미 안내한 말]이 있으면 그 문장은 화면에서 소개 맨 앞에 이미 나갔다. 같은 말을 반복하지 말고",
                "다른 대괄호 표시([다른 제안], [이미 안내한 말] 등)는 답에 쓰지 않는다",
                "냉장고에 없는 재료 이야기는 하지 않는다")
                .doesNotContain("먼저 말할 것", "말씀하신 버섯");
    }

    @Test
    @DisplayName("임박은 짧게·말 그대로, 선택 재료는 주재료로, 장볼 재료 표현, 카드 정보에 없는 특징·찾았다는 단정 금지는 그대로다")
    void keepsExistingRules() {
        assertThat(prompt).contains("내일까지인 고추", "적힌 그대로 쓰고 바꾸지 않는다", "쓰는 냉장고 재료를 전부 나열하지 않는다",
                "그 재료가 주재료로 들어간다고 말할 수 있다", "\"부족한 재료\"라고 하지 않고 \"장볼 재료\"로 말한다",
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
