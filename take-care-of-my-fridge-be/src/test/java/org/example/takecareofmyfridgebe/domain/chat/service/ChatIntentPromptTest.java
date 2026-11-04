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
}
