package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 의도·조건 뽑기 프롬프트(prompts/chat-intent.txt)에 꼭 있어야 하는 지시
class ChatIntentPromptTest {

    private static String prompt;

    @BeforeAll
    static void readPrompt() throws IOException {
        prompt = new ClassPathResource("prompts/chat-intent.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("차가운·시원한 같은 특징을 빼지 말고 구체적인 요리 이름 키워드로 바꾸라고 예시와 함께 지시한다")
    void instructsFeatureToDishKeywords() {
        assertThat(prompt).contains("특징도 빼지 말고 그런 요리 이름으로 바꿔 넣는다",
                "\"차가운 요리 추천해줘\" → keywords: 샐러드, 냉채, 냉국, 무침, 냉파스타, 냉면",
                "\"시원한 거 먹고 싶어\" → keywords: 냉국, 냉면, 화채, 샐러드, 냉채",
                "\"차가운 면\" → keywords: 냉면, 막국수, 콩국수, 냉메밀 / moodCategories: 면류",
                "\"따뜻한 국물 요리\" → keywords: 빈 목록 / moodCategories: 국, 찌개, 전골, 수프, 스튜")
                .doesNotContain("넓은 말");
    }

    @Test
    @DisplayName("원한 특징·요리 표현을 preferenceText로 그대로 쓰고, 뭐 먹지?처럼 없으면 빈 문자열로 쓰라고 지시한다")
    void instructsPreferenceText() {
        assertThat(prompt).contains("- preferenceText:", "\"차가운 요리 추천해줘\" → \"차가운 요리\"", "\"뭐 먹지?\" → 빈 문자열");
    }

    @Test
    @DisplayName("요리 이름을 말하면 그 이름과 비슷한 요리를 키워드에 먼저 넣고, 탕이 붙은 튀김 요리를 국으로 보지 말라고 지시한다")
    void instructsDishNameFirst_andTangIsNotAlwaysSoup() {
        assertThat(prompt).contains("그 이름을 맨 앞에, 이어서 비슷한 요리 이름을 넣는다",
                "moodCategories는 그 요리의 분류가 확실할 때만 넣는다",
                "탕수육·김피탕·깐풍기는 튀김 요리다",
                "\"김피탕 같은 요리 추천해줘\" → keywords: 김피탕, 탕수육, 깐풍기 / moodCategories: 튀김 / dishText: 김피탕");
    }

    @Test
    @DisplayName("첫 질문용 고정 프롬프트에는 이어지는 말 규칙이 없고, 이어지는 말 규칙은 따로 다시 추천·레시피 자세히·대상 칸을 지시한다")
    void keepsFollowUpRulesSeparate() throws IOException {
        String followUp = new ClassPathResource("prompts/chat-intent-followup.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).doesNotContain("REFINE", "RECIPE_DETAIL", "[직전 추천]");
        assertThat(followUp).contains("REFINE: 직전 추천을 조건을 바꿔 다시 받고 싶은 말",
                "바꾸라고 하지 않은 조건은 [직전 조건] 그대로 쓴다",
                "RECIPE_DETAIL: [직전 추천]이나 [이전 추천]에 있는 레시피 하나",
                "targetIndex: [직전 추천] 번호(1부터)", "targetName: 말한 레시피 이름",
                "\"매운 거 말고\" → removed: TASTE", "\"시간 상관없이\" → removed: MAX_MINUTES",
                "REFINE이 아니면 removed는 빈 목록",
                "새 요리 이름이나 새 조건으로 처음부터 추천해 달라는 말(\"김치찌개 먹고 싶어\")은 RECOMMEND");
    }
}
