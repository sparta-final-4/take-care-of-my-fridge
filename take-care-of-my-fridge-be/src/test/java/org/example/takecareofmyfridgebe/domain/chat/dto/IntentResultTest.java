package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentResultTest {

    @Test
    @DisplayName("맛·피할 맛은 정해진 맛 이름만 Member.Taste로 바꾸고, 없으면 빈 목록이다")
    void convertsKnownTastesOnly() {
        IntentResult result = new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY", "HOT"), null, null, null, null, null, null,
                null, null, null, null, null, List.of("SALTY", "없는맛"));

        assertThat(result.tastes()).containsExactly(Taste.SPICY);
        assertThat(result.avoidedTastes()).containsExactly(Taste.SALTY);
        assertThat(IntentResult.general().tastes()).isEmpty();
        assertThat(IntentResult.general().avoidedTastes()).isEmpty();
    }

    @Test
    @DisplayName("뺄 조건은 정해진 종류만 RefineCondition으로 바꾸고, 없으면 빈 집합이다")
    void convertsRemovedConditions() {
        IntentResult result = new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null, null, null, null,
                null, null, List.of("TASTE", "MAX_MINUTES", "없는 조건"));

        assertThat(result.removedConditions()).containsExactlyInAnyOrder(RefineCondition.TASTE, RefineCondition.MAX_MINUTES);
        assertThat(IntentResult.general().removedConditions()).isEmpty();
    }

    @Test
    @DisplayName("레시피 자세히 질문 종류는 정해진 이름만 DetailQuestion으로 바꾸고, 없거나 모르는 값이면 null이다")
    void convertsDetailQuestion() {
        IntentResult howTo = new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                2, null, null, null, "HOW_TO");
        IntentResult unknown = new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                2, null, null, null, "STEP");

        assertThat(howTo.question()).isEqualTo(DetailQuestion.HOW_TO);
        assertThat(unknown.question()).isNull();
        assertThat(IntentResult.general().question()).isNull();
    }

    @Test
    @DisplayName("서버가 맛·이름·요리 이름을 바꿔도 냉장고 보기 범위를 버리지 않는다")
    void keepsFridgeScope_whenServerChangesFields() {
        IntentResult result = new IntentResult(ChatIntent.RECOMMEND, List.of("SPICY"), List.of(), 0, List.of(), List.of(), false,
                List.of("김치찌개"), null, null, 0, null, List.of(), List.of(), null, "IMMINENT");

        assertThat(result.withTastes(List.of(), List.of("SPICY")).fridgeScope()).isEqualTo("IMMINENT");
        assertThat(result.withNamed(List.of("두부"), List.of(), null).fridgeScope()).isEqualTo("IMMINENT");
        assertThat(result.withCategories(List.of(), List.of("김치찌개"), "김치찌개", List.of()).fridgeScope()).isEqualTo("IMMINENT");
    }

    @Test
    @DisplayName("서버가 이름만 말한 말로 채우면 추천이 되고 재료·키워드·요리 이름만 바뀐다")
    void fillsNamed_whenServerNames() {
        IntentResult result = new IntentResult(ChatIntent.GENERAL, List.of("SPICY"), null, null, null, null, null, null, null, null)
                .withNamed(List.of(), List.of("닭볶음탕"), "닭볶음탕");

        assertThat(result.intent()).isEqualTo(ChatIntent.RECOMMEND);
        assertThat(result.wantedTastes()).containsExactly("SPICY");
        assertThat(result.includeIngredients()).isEmpty();
        assertThat(result.keywords()).containsExactly("닭볶음탕");
        assertThat(result.dishText()).isEqualTo("닭볶음탕");
    }

    // 요리 이름·키워드만 정한 AI 결과 (나머지 칸은 말하지 않음)
    private static IntentResult dish(String dishText, List<String> keywords) {
        return new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null, keywords, dishText, null);
    }

    @Test
    @DisplayName("검색 키워드는 요리 이름을 맨 앞에 두고 나머지 AI 키워드를 순서대로 붙인다")
    void putsDishFirst_inSearchKeywords() {
        assertThat(dish("김피탕", List.of("탕수육", "김피탕", "깐풍기")).searchKeywords())
                .containsExactly("김피탕", "탕수육", "깐풍기");
    }

    @Test
    @DisplayName("검색 키워드에서 띄어쓰기만 다른 이름은 앞의 것 하나만 남기고 최대 6개까지 둔다")
    void dropsDuplicatesAndLimits_inSearchKeywords() {
        assertThat(dish("나가사키 짬뽕", List.of("나가사키짬뽕", "짬뽕")).searchKeywords()).containsExactly("나가사키 짬뽕", "짬뽕");
        assertThat(dish("김치찌개", List.of("부대찌개", "순두부찌개", "김치전골", "김치찜", "참치찌개", "된장찌개")).searchKeywords())
                .hasSize(IntentResult.MAX_KEYWORDS).startsWith("김치찌개").doesNotContain("된장찌개");
    }

    @Test
    @DisplayName("요리 이름이 없으면 AI 키워드를 그대로 검색 키워드로 쓴다")
    void usesAiKeywords_whenNoDish() {
        assertThat(dish(null, List.of("냉국", "냉면")).searchKeywords()).containsExactly("냉국", "냉면");
    }

    @Test
    @DisplayName("\"김피탕\"과 \"김피탕 같은 요리 추천해줘\"의 AI 키워드가 달라도 요리 이름이 같으면 검색 키워드 맨 앞은 같다")
    void startsWithSameDish_whenOnlyKeywordsDiffer() {
        assertThat(dish("김피탕", List.of()).searchKeywords()).containsExactly("김피탕");
        assertThat(dish("김피탕", List.of("김피탕")).searchKeywords()).containsExactly("김피탕");
    }
}
