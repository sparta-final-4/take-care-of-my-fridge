package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// OpenAI 꺼내기 프롬프트(prompts/chat-intent.txt, chat-intent-followup.txt)에 꼭 있어야 하는 지시
class ChatIntentPromptTest {

    private static String prompt;

    @BeforeAll
    static void readPrompt() throws IOException {
        prompt = new ClassPathResource("prompts/chat-intent.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("차가운·시원한 같은 특징을 빼지 말고 구체적인 요리 이름 키워드로 바꾸라고 예시와 함께 지시한다")
    void instructsFeatureToDishKeywords() {
        assertThat(prompt).contains("특징도 빼지 말고 그런 요리 이름으로 바꾼다",
                "keywords: 특징만 말했을 때(\"차가운 요리\", \"시원한 국물\") 그 특징에 맞는 구체적인 요리 이름을 넣는다",
                "\"차가운 요리 추천해줘\" → dishText는 비운다 / keywords: 샐러드, 냉채, 냉국, 무침, 냉파스타, 냉면",
                "\"차가운 면\" → keywords: 냉면, 막국수, 콩국수, 냉메밀 / moodCategories: 면류",
                "분류로 딱 맞게 나뉘는 말(\"따뜻한 국물\")은 moodCategories로만 쓴다")
                .doesNotContain("넓은 말");
    }

    @Test
    @DisplayName("원한 특징·요리 표현을 preferenceText로 그대로 쓰고, 뭐 먹지?처럼 없으면 빈 문자열로 쓰라고 지시한다")
    void instructsPreferenceText() {
        assertThat(prompt).contains("- preferenceText:", "\"차가운 요리 추천해줘\" → \"차가운 요리\"", "\"뭐 먹지?\" → 빈 문자열",
                "keywords로 바꾼 특징(\"차가운\", \"시원한\")은 꼭 쓴다");
    }

    @Test
    @DisplayName("dishText는 원하는 요리와 비슷한 것을 찾을 기준 요리의 이름이고, 뺄 요리·분류·재료와 레시피·만드는 법 같은 말은 넣지 않으며, 맛·재료에 붙은 말고·빼고는 요리 이름을 지우지 않는다고 일반 예시로 지시한다")
    void instructsDishText() {
        assertThat(prompt).contains(
                "- dishText: 사용자가 원하는 요리이거나, 비슷한 것을 찾을 기준으로 말한 요리의 이름. 처음 듣는 요리 이름이어도 사용자가 말한 그대로 쓴다. \"○○ 같은 것\"의 ○○도 여기에 쓴다.",
                "빼 달라거나 피하고 싶다고 말한 요리는 쓰지 않는다. \"말고\"·\"빼고\"가 맛이나 재료에 붙었으면 요리 이름은 그대로 쓴다.",
                "분류나 종류(찌개, 국물, 디저트)와 재료 이름은 요리 이름이 아니다.",
                "\"짠 거 말고 된장찌개 같은 거\" → dishText: 된장찌개 / \"된장찌개 말고 다른 거\" → dishText는 비운다",
                "\"레시피\", \"만드는 법\", \"추천\" 같은 말은 요리 이름에 넣지 않는다",
                "\"○○ 같은 요리 추천해줘\" → dishText: ○○ / keywords: ○○",
                "\"닭볶음탕 레시피 알려줘\" → dishText: 닭볶음탕 / keywords: 닭볶음탕",
                "\"시원한 국물 요리\" → dishText는 비운다 / keywords: 냉국, 오이냉국, 냉면, 물회",
                "\"디저트 추천해줘\" → dishText는 비운다 / keywords: 빈 목록 / moodCategories: 디저트",
                "dishText·preferenceText 빈 문자열");
    }

    @Test
    @DisplayName("요리 이름을 말했으면 keywords에는 그 이름만 쓰고, 비슷한 요리는 꺼내기에서 묻지 않는다 (후보 검색에서 따로 묻는다)")
    void keepsDishOnlyInKeywords_withoutSimilarDishes() {
        assertThat(prompt).contains("요리 이름을 말했으면 keywords에는 그 이름만 쓴다.",
                "\"김치찌개 먹고 싶어\" → dishText: 김치찌개 / keywords: 김치찌개 / moodCategories: 찌개");
        assertThat(prompt).doesNotContain("similarDishes", "비슷한 요리");
    }

    @Test
    @DisplayName("의도는 Jev가 정하므로 첫 질문 프롬프트에 의도 고르기·이어짐·맛·분류 칸 지시와 분류 목록이 없다")
    void dropsIntentChoice() {
        assertThat(prompt).doesNotContain("- intent", "RECOMMEND:", "GENERAL:", "OFF_TOPIC", "BLOCKED", "SELF_HARM",
                "followsPrevious", "wantedTastes", "avoidTastes", "- moodCategories:", "%CATEGORIES%", "분류 목록");
        assertThat(prompt).startsWith("너는 요리 상담 메시지를 읽고 추천 조건과 요리 이름·재료를 정해진 JSON으로만 뽑는다.");
    }

    @Test
    @DisplayName("냉장고 보기 범위와 찾거나 살 이름을 꺼내라는 지시는 그대로 두고, \"재료·밀키트·상품\"은 이름이 아니라고 지시한다")
    void keepsFridgeAndShopExtraction() {
        assertThat(prompt).contains(
                "- fridgeScope: FRIDGE일 때만 쓴다",
                "기본 양념·조미료 → SEASONING",
                "fridgeScope ALL",
                "FRIDGE·SHOP이면 찾거나 살 재료·상품 이름(\"된장국 밀키트\")",
                "\"재료\"·\"밀키트\"·\"상품\"이라는 말은 이름이 아니다");
    }

    @Test
    @DisplayName("비슷한 요리를 늘릴지 AI가 정하던 규칙과 특정 요리 사례(김피탕·탕수육·깐풍기, 탕 붙은 튀김) 지시는 두지 않고, 후속 규칙도 되풀이하지 않는다")
    void dropsCaseSpecificDishRules() throws IOException {
        String followUp = followUp();

        assertThat(prompt).doesNotContain("짬뽕", "레시피에 없을 법하면", "\"같은\"이 없어도", "세트 메뉴", "그 이름을 맨 앞에",
                "김피탕", "탕수육", "깐풍기", "튀김 요리다", "탕짜면", "짬짜면");
        assertThat(prompt).contains("moodCategories는 요리의 분류가 확실할 때만 넣는다");
        // 후속 규칙은 요리 이름 늘리기를 되풀이하지 않고 첫 질문 규칙을 따르게 한다
        assertThat(followUp).contains("첫 질문 규칙대로").doesNotContain("줄임말", "similarDishes");
    }

    @Test
    @DisplayName("후속 규칙은 이어짐 판단·의도 이름 없이, 이어지는 말이면 조건 칸에 쓸 값과 레시피 자세히 대상·질문 종류를 꺼내라고 지시한다")
    void instructsFollowUpExtraction() throws IOException {
        String followUp = followUp();

        assertThat(followUp).doesNotContain("followsPrevious", "REFINE", "RECIPE_DETAIL", "RECOMMEND", "이어짐,");
        assertThat(followUp).contains(
                "[이어지는 말에서 꺼내기]",
                "이번 말의 분류·요리 이름이 [직전 조건]보다 앞선다",
                "[직전 추천]의 조건(맛, 재료, 종류)을 이어받으면서 일부만 바꿔 달라는 말이면:",
                "조건 칸에는 [직전 조건]에 이번 말을 반영한 최종 조건을 쓴다(바꾸라고 하지 않은 조건은 그대로",
                "includeIngredients·excludeIngredients에는 이번에 말한 재료만 쓴다.",
                "\"시간 상관없이\" → MAX_MINUTES",
                "이 상담에서 추천한 레시피 하나의 만드는 법·시간·재료·팁을 묻는 말이면",
                "targetIndex: [직전 추천] 번호(1부터", "targetName: 말한 레시피 이름",
                "detailQuestion: HOW_TO(만드는 법), TIME(조리 시간), INGREDIENTS(재료), TIP(팁), OTHER 중 하나",
                "\"두 번째 거 어떻게 만들어?\" → targetIndex: 2, HOW_TO",
                "그 밖은 첫 질문 규칙대로 꺼내고 [직전 조건]을 잇지 않는다.");
    }

    @Test
    @DisplayName("후속 규칙은 서버가 확인하는 맛 예시 없이 removed·detailQuestion 예시 두 줄로 짧게 두며, 화면에 나오면 안 되는 말은 예시에 없다")
    void keepsFollowUpRulesShort() throws IOException {
        String followUp = followUp();

        assertThat(followUp).doesNotContain("wantedTastes: SPICY", "avoidTastes: SPICY", "\"맛은 상관없어\"", "\"두부 안 넣어도 돼\"",
                "\"맛있게 하는 팁\"", "생략", "[다른 제안]", "[이미 안내한 말]");
        assertThat(followUp.lines().filter(line -> line.contains("예)"))).hasSize(2);
        // 이어짐 판단·의도 예시를 지워 1,300자 아래에서 900자 아래로 줄였다
        assertThat(followUp.length()).isLessThan(900);
    }

    @Test
    @DisplayName("의도·맛·분류 지시를 지워 첫 질문 프롬프트를 2,000자 아래로 둔다 (의도 판단을 하던 때는 3,300자 아래)")
    void keepsIntentPromptShort() {
        assertThat(prompt.length()).isLessThan(2_000);
        assertThat(prompt).doesNotContain("주식으로 먹을 거", "장보기 목록 짜줘", "술안주", "말하지 않았으면 빈 목록");
    }

    private static String followUp() throws IOException {
        return new ClassPathResource("prompts/chat-intent-followup.txt").getContentAsString(StandardCharsets.UTF_8);
    }
}
