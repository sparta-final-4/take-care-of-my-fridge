package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentResultTest {

    private static final List<String> CATEGORIES = List.of("국", "찌개", "샐러드");

    @Test
    @DisplayName("없는 맛과 목록에 없는 분류는 버리고, 소문자 맛은 대문자로 맞춘다")
    void dropsUnknownTastesAndCategories() {
        IntentResult raw = new IntentResult(ChatIntent.RECOMMEND,
                List.of("spicy", "HOT", "SALTY", "SPICY"), List.of("국", "양념", "스튜", "국"), 30,
                null, null, true, null, null, null);

        IntentResult result = raw.sanitize(CATEGORIES);

        assertThat(result.wantedTastes()).containsExactly("SPICY", "SALTY");
        assertThat(result.tastes()).containsExactly(Taste.SPICY, Taste.SALTY);
        assertThat(result.moodCategories()).containsExactly("국");
        assertThat(result.maxMinutes()).isEqualTo(30);
        assertThat(result.useSelectedOnly()).isTrue();
    }

    @Test
    @DisplayName("분류 이름인 키워드도 정리에서는 그대로 둔다 (분류 이름 빼기는 서버 보정 IntentCorrector가 한 곳에서 한다)")
    void keepsCategoryKeywords_whenSanitizing() {
        IntentResult copied = new IntentResult(ChatIntent.RECOMMEND, null, List.of("국", "찌개"), null,
                null, null, null, List.of("국", "찌개"), null, null).sanitize(CATEGORIES);

        assertThat(copied.keywords()).containsExactly("국", "찌개");
        assertThat(copied.moodCategories()).containsExactly("국", "찌개");
    }

    @Test
    @DisplayName("말하지 않은 칸(null)은 정리한 뒤에도 null로 두고, 의도가 없으면 GENERAL로 본다")
    void keepsNull_whenFieldIsNotSaid() {
        IntentResult result = new IntentResult(null, null, null, null, null, null, null, null, null, null).sanitize(CATEGORIES);

        assertThat(result.intent()).isEqualTo(ChatIntent.GENERAL);
        assertThat(result.wantedTastes()).isNull();
        assertThat(result.moodCategories()).isNull();
        assertThat(result.maxMinutes()).isNull();
        assertThat(result.includeIngredients()).isNull();
        assertThat(result.excludeIngredients()).isNull();
        assertThat(result.useSelectedOnly()).isNull();
        assertThat(result.tastes()).isEmpty();
    }

    @Test
    @DisplayName("조리 시간이 0 이하면 null로 바꾼다")
    void returnsNullMaxMinutes_whenNotPositive() {
        IntentResult result = new IntentResult(ChatIntent.RECOMMEND, null, null, 0, null, null, null, null, null, null)
                .sanitize(CATEGORIES);

        assertThat(result.maxMinutes()).isNull();
    }

    @Test
    @DisplayName("재료 이름은 앞뒤 공백을 지우고 빈 이름·null·중복을 뺀다")
    void trimsIngredientNames() {
        IntentResult raw = new IntentResult(ChatIntent.RECOMMEND, null, null, null,
                Arrays.asList(" 두부 ", "", null, "두부", "버섯"), List.of("오이 "), null, null, null, null);

        IntentResult result = raw.sanitize(CATEGORIES);

        assertThat(result.includeIngredients()).containsExactly("두부", "버섯");
        assertThat(result.excludeIngredients()).containsExactly("오이");
    }

    @Test
    @DisplayName("키워드는 앞뒤 공백·빈 값·중복을 빼고 6개까지만 남기고, 빈 요리 표현은 null로 바꾼다")
    void trimsKeywordsToSix() {
        IntentResult raw = new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null,
                List.of(" 냉면 ", "막국수", "", "냉면", "콩국수", "냉메밀", "냉파스타", "비빔국수", "물회"), "  ", " ");

        IntentResult result = raw.sanitize(CATEGORIES);

        assertThat(result.keywords()).containsExactly("냉면", "막국수", "콩국수", "냉메밀", "냉파스타", "비빔국수");
        assertThat(result.dishText()).isNull();
        assertThat(result.preferenceText()).isNull();
    }

    @Test
    @DisplayName("레시피 자세히 대상은 카드 번호가 0 이하면 null, 이름이 비어 있으면 null로 정리한다")
    void sanitizesTargetFields() {
        IntentResult picked = new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                2, " 깐풍기 ").sanitize(CATEGORIES);
        IntentResult empty = new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                0, " ").sanitize(CATEGORIES);

        assertThat(picked.targetIndex()).isEqualTo(2);
        assertThat(picked.targetName()).isEqualTo("깐풍기");
        assertThat(empty.targetIndex()).isNull();
        assertThat(empty.targetName()).isNull();
    }

    @Test
    @DisplayName("뺄 조건(removed)은 정해진 종류만 남기고 소문자는 대문자로 맞추며, 없으면 빈 집합이다")
    void sanitizesRemovedConditions() {
        IntentResult result = new IntentResult(ChatIntent.REFINE, null, null, null, null, null, null, null, null, null,
                null, null, List.of("taste", "MAX_MINUTES", "없는 조건")).sanitize(CATEGORIES);

        assertThat(result.removed()).containsExactly("TASTE", "MAX_MINUTES");
        assertThat(result.removedConditions()).containsExactlyInAnyOrder(RefineCondition.TASTE, RefineCondition.MAX_MINUTES);
        assertThat(IntentResult.general().removedConditions()).isEmpty();
    }

    @Test
    @DisplayName("피할 맛(avoidTastes)은 정해진 맛 이름만 남기고 소문자는 대문자로 맞추며, 없으면 빈 목록이다")
    void sanitizesAvoidTastes() {
        IntentResult result = new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null, null, null, null,
                null, null, null, List.of("spicy", "없는맛")).sanitize(CATEGORIES);

        assertThat(result.avoidedTastes()).containsExactly(Taste.SPICY);
        assertThat(IntentResult.general().avoidedTastes()).isEmpty();
    }

    @Test
    @DisplayName("레시피 자세히 질문 종류는 정해진 이름만 남기고 소문자는 대문자로 맞추며, 없거나 모르는 값이면 null이다")
    void sanitizesDetailQuestion() {
        IntentResult howTo = new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                2, null, null, null, " how_to ").sanitize(CATEGORIES);
        IntentResult unknown = new IntentResult(ChatIntent.RECIPE_DETAIL, null, null, null, null, null, null, null, null, null,
                2, null, null, null, "STEP").sanitize(CATEGORIES);

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
        assertThat(result.withNamed(List.of("두부"), List.of()).fridgeScope()).isEqualTo("IMMINENT");
        assertThat(result.withDishFromKeywords("김치찌개 먹고 싶어").fridgeScope()).isEqualTo("IMMINENT");
    }

    @Test
    @DisplayName("요리 이름은 첫 키워드가 메시지에 띄어쓰기와 상관없이 그대로 있을 때만 그 이름이다")
    void picksDish_whenFirstKeywordInMessage() {
        IntentResult result = new IntentResult(ChatIntent.RECOMMEND, null, null, null, null, null, null,
                List.of("장어 조림", "장어구이"), "엉뚱한 이름", null);

        assertThat(result.withDishFromKeywords("장어조림 먹고 싶어").dishText()).isEqualTo("장어 조림");
        assertThat(result.withDishFromKeywords("장어 요리 추천해줘").dishText()).isNull();
        assertThat(IntentResult.general().withDishFromKeywords("뭐 먹지?").dishText()).isNull();
    }
}
