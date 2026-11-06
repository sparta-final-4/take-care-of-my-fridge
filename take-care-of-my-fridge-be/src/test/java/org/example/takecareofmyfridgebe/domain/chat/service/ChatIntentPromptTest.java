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
        assertThat(followUp).contains("REFINE: 직전 추천을 바꿔 달라는 말(\"다른\", \"또\", \"말고 다른\")",
                "바꾸라고 하지 않은 조건은 [직전 조건] 그대로 쓴다",
                "RECIPE_DETAIL: 이 상담에서 추천했던 레시피 하나",
                "[직전 추천]·[이전 추천]에 없어도 \"아까 그 ○○\"처럼 앞서 추천받은 레시피 이름을 말하면 RECIPE_DETAIL이다",
                "targetIndex: [직전 추천] 번호(1부터)", "targetName: 말한 레시피 이름",
                "맛은 첫 질문 규칙대로 쓰고, 피할 맛은 [직전 조건]의 피할 맛을 이어 쓴다",
                "\"시간 상관없이\" → removed: MAX_MINUTES / \"오이 넣어도 돼\" → removed: EXCLUDE",
                "REFINE이 아니면 removed는 빈 목록",
                "detailQuestion: HOW_TO(만드는 법), TIME(조리 시간), INGREDIENTS(재료), TIP(팁), OTHER 중 하나",
                "\"두 번째 거 어떻게 만들어?\" → targetIndex: 2, HOW_TO",
                "새 요리 이름이나 새 조건으로 처음부터 추천해 달라는 말(\"김치찌개 먹고 싶어\")");
    }

    @Test
    @DisplayName("후속 규칙은 이번 말의 분류를 앞 대화보다 앞세우고, 같은 요청·코스 더하기는 새 추천, 다른 것 요청은 다시 추천으로 예시와 함께 지시한다")
    void prefersCurrentCategory_inFollowUpRules() throws IOException {
        String followUp = new ClassPathResource("prompts/chat-intent-followup.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(followUp).contains(
                "이번 말의 분류·요리 이름이 [직전 조건]보다 앞선다",
                "같은 요청을 다시 해도 조건을 비우지 않고 이번 말에서 뽑는다",
                "코스를 더하는 말(\"디저트도\", \"후식으로\", \"국도 같이\")은 RECOMMEND이고 [직전 조건]을 잇지 않는다",
                "직전이 김피탕일 때 \"디저트 추천해줘\" → RECOMMEND, 분류 디저트, 키워드 없음",
                "\"다른 디저트\" → REFINE, 디저트",
                "\"다른 거\" → REFINE, 분류 없음",
                "\"디저트 말고 국\" → REFINE, 국, removed: CATEGORY");
    }

    @Test
    @DisplayName("후속 규칙은 서버가 확인하는 맛 예시를 빼고 removed·detailQuestion 예시를 줄여 짧게 두며, 화면에 나오면 안 되는 말은 예시에 없다")
    void keepsFollowUpRulesShort() throws IOException {
        String followUp = new ClassPathResource("prompts/chat-intent-followup.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(followUp).doesNotContain("wantedTastes: SPICY", "avoidTastes: SPICY", "\"맛은 상관없어\"", "\"두부 안 넣어도 돼\"",
                "\"맛있게 하는 팁\"", "생략", "[다른 제안]", "[이미 안내한 말]");
        assertThat(followUp.lines().filter(line -> line.contains("예)"))).hasSize(5);
        // 35% 줄이기 전(1,809자)보다 짧게 둔다
        assertThat(followUp.length()).isLessThan(1_550);
    }

    @Test
    @DisplayName("첫 질문 프롬프트에 원하는 맛과 피할 맛 예시를 나란히 둔다 (좀 더 매운 걸로 → wanted, 매운 거 말고 → avoid)")
    void instructsAvoidTastes() {
        assertThat(prompt).contains("avoidTastes: 피하고 싶다고 말한 맛",
                "\"좀 더 매운 걸로\", \"매운 거 먹고 싶어\", \"칼칼한 거\" → wantedTastes: SPICY, avoidTastes: 빈 목록",
                "\"매운 거 말고\", \"안 맵게\", \"덜 맵게\" → avoidTastes: SPICY, wantedTastes: 빈 목록",
                "\"매운 거 말고 짭짤하게\" → avoidTastes: SPICY, wantedTastes: SALTY",
                "피하고 싶다는 맛은 wantedTastes에 넣지 않는다");
    }
}
